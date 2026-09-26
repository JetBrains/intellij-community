#!/usr/bin/env python3
"""Notebook helper CLI for JetBrains experiment notebooks."""

# /// script
# requires-python = ">=3.10"
# dependencies = [
#   "ipykernel",
#   "jupyter-client",
#   "matplotlib",
#   "nbconvert",
#   "numpy",
#   "pandas",
#   "plotly",
# ]
# ///

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

def _find_repo_root() -> Path:
    for parent in Path(__file__).resolve().parents:
        if (parent / ".git").exists():
            return parent
    raise RuntimeError(f"No .git found among parents of {__file__}")

_REPO_ROOT = _find_repo_root()

def _find_uv() -> str:
    # Full monorepo: uv.cmd lives under community/tools/ relative to repo root.
    # Community-only checkout: uv.cmd lives under tools/ relative to repo root.
    for candidate in [
        _REPO_ROOT / "community" / "tools" / "uv.cmd",
        _REPO_ROOT / "tools" / "uv.cmd",
    ]:
        if candidate.exists():
            return str(candidate)
    return "uv"  # Fall back to system uv (e.g. when running outside the monorepo).

_UV = _find_uv()

_NB_PACKAGES = [
    "--with", "nbconvert",
    "--with", "jupyter-client",
    "--with", "ipykernel",
    "--with", "numpy",
    "--with", "pandas",
    "--with", "matplotlib",
    "--with", "plotly",
]


def _load(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def _save(path: str, nb: dict) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(nb, f, indent=1, ensure_ascii=False)
        f.write("\n")


def _cell_source(cell: dict) -> str:
    src = cell.get("source", "")
    return "".join(src) if isinstance(src, list) else src


def _to_source_lines(text: str) -> list[str]:
    """Convert plain text to the list-of-strings format Jupyter uses internally."""
    if not text:
        return []
    lines = text.splitlines(keepends=True)
    # Jupyter convention: the last line must not have a trailing newline.
    if lines and lines[-1].endswith("\n"):
        lines[-1] = lines[-1][:-1]
    return lines


# On Windows the default stdout encoding is cp1252, which cannot represent
# Unicode characters such as emoji. Reconfigure to UTF-8 so list-cells and
# get-cell output is readable when the terminal supports it.
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass


def _die(msg: str) -> None:
    print(msg, file=sys.stderr)
    sys.exit(1)


def _resolve_cell(cells: list, n: int | None, *, allow_append: bool = False) -> int:
    resolved = len(cells) - 1 if n is None else n
    upper = len(cells) if allow_append else len(cells) - 1
    if resolved < 0 or resolved > upper:
        _die(f"Error: cell index {resolved} out of range (0..{upper})")
    return resolved


def cmd_list_cells(notebook: str) -> None:
    nb = _load(notebook)
    for i, cell in enumerate(nb["cells"]):
        first = _cell_source(cell).split("\n", 1)[0][:80]
        line = f"[{i}] {cell['cell_type']:8s}  {first}"
        sys.stdout.buffer.write((line + "\n").encode(sys.stdout.encoding or "utf-8", errors="replace"))


def cmd_get_cell(notebook: str, n: int) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, n)
    # Always end with a newline so the shell prompt appears on its own line.
    print(_cell_source(cells[n]))


def cmd_set_cell(notebook: str, n: int, file: str) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, n)
    text = sys.stdin.read() if file == "-" else Path(file).read_text(encoding="utf-8")
    cells[n]["source"] = _to_source_lines(text)
    if cells[n]["cell_type"] == "code":
        # Clear stale outputs so the notebook doesn't show results from old code.
        cells[n]["outputs"] = []
        cells[n]["execution_count"] = None
    _save(notebook, nb)
    print(f"Cell {n} updated.", file=sys.stderr)


def cmd_insert_cell(notebook: str, n: int, cell_type: str, file: str) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, n, allow_append=True)
    text = sys.stdin.read() if file == "-" else Path(file).read_text(encoding="utf-8")
    if cell_type == "code":
        cell: dict = {
            "cell_type": "code",
            "metadata": {},
            "source": _to_source_lines(text),
            "outputs": [],
            "execution_count": None,
        }
    else:
        cell = {
            "cell_type": "markdown",
            "metadata": {},
            "source": _to_source_lines(text),
        }
    cells.insert(n, cell)
    _save(notebook, nb)
    print(f"Cell inserted at position {n} (total: {len(cells)}).", file=sys.stderr)


_VISIBLE_TAG = "nb:visible"


def _is_visible(cell: dict) -> bool:
    """Return True if the cell is tagged nb:visible."""
    return _VISIBLE_TAG in cell.get("metadata", {}).get("tags", [])


def _set_source_hidden(cell: dict, hidden: bool) -> None:
    meta = cell.setdefault("metadata", {})
    jup = meta.setdefault("jupyter", {})
    if hidden:
        jup["source_hidden"] = True
    else:
        jup.pop("source_hidden", None)
        if not jup:
            meta.pop("jupyter", None)


def cmd_collapse_cells(notebook: str, keep: list[int]) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    # When no cell carries the nb:visible tag, fall back to the keep list.
    any_tagged = any(_is_visible(c) for c in cells if c["cell_type"] == "code")
    collapsed = expanded = 0
    for i, cell in enumerate(cells):
        if cell["cell_type"] != "code":
            continue
        visible = _is_visible(cell) if any_tagged else (i in keep)
        _set_source_hidden(cell, not visible)
        if visible:
            expanded += 1
        else:
            collapsed += 1
    _save(notebook, nb)
    print(f"Collapsed {collapsed} code cell(s), kept {expanded} visible.", file=sys.stderr)


def cmd_expand_cells(notebook: str) -> None:
    nb = _load(notebook)
    count = 0
    for cell in nb["cells"]:
        if cell["cell_type"] == "code" and cell.get("metadata", {}).get("jupyter", {}).get("source_hidden"):
            _set_source_hidden(cell, False)
            count += 1
    _save(notebook, nb)
    print(f"Expanded {count} code cell(s).", file=sys.stderr)


def cmd_delete_cell(notebook: str, n: int) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, n)
    cells.pop(n)
    _save(notebook, nb)
    print(f"Cell {n} deleted (total: {len(cells)}).", file=sys.stderr)


def _extract_diff_blocks(text: str, cell_n: int) -> list[str]:
    """Extract unified-diff content from fenced code blocks in a cell source.

    Raises SystemExit if no diff block is found — callers must not proceed
    without actual diff content.
    """
    blocks = re.findall(r"```[^\n]*\n(.*?)```", text, re.DOTALL)
    diffs = [b for b in blocks if b.lstrip().startswith(("diff --git", "--- "))]
    if not diffs:
        _die(
            f"Error: no diff block found in cell {cell_n}. "
            "Expected a fenced block whose content starts with 'diff --git' or '--- '."
        )
    return diffs


def cmd_apply_patch(notebook: str, cell_n: int | None, repo: str) -> None:
    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, cell_n)
    patches = _extract_diff_blocks(_cell_source(cells[n]), n)
    for i, patch_text in enumerate(patches, 1):
        label = f"patch {i}/{len(patches)}"
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".patch", delete=False, encoding="utf-8"
        ) as f:
            f.write(patch_text)
            tmp = f.name
        try:
            rev = subprocess.run(
                ["git", "-C", repo, "apply", "--reverse", "--check", tmp],
                capture_output=True,
            )
            if rev.returncode == 0:
                print(f"{label}: already applied, skipping.", file=sys.stderr)
                continue
            fwd = subprocess.run(
                ["git", "-C", repo, "apply", "--check", tmp],
                capture_output=True, text=True,
            )
            if fwd.returncode == 0:
                subprocess.run(["git", "-C", repo, "apply", tmp], check=True)
                print(f"{label}: applied.", file=sys.stderr)
                continue

            # Context lines may no longer match if the repo has moved on since
            # the patch was generated. Try a 3-way merge, which uses the blob
            # objects referenced in the diff header instead of exact context.
            fwd3 = subprocess.run(
                ["git", "-C", repo, "apply", "--3way", "--check", tmp],
                capture_output=True, text=True,
            )
            if fwd3.returncode == 0:
                subprocess.run(["git", "-C", repo, "apply", "--3way", tmp], check=True)
                print(f"{label}: applied (3-way merge).", file=sys.stderr)
                continue

            print(f"{label}: cannot apply:\n{fwd.stderr.strip()}", file=sys.stderr)
            print("Tip: check out the commit the patch was generated from, then retry.", file=sys.stderr)
            sys.exit(1)
        finally:
            os.unlink(tmp)


def cmd_update_patch(notebook: str, cell_n: int | None, repo: str, staged: bool) -> None:
    """Replace the first diff fence in the target cell with the current git diff."""
    git_args = ["git", "-C", repo, "diff"]
    if staged:
        git_args.append("--cached")
    result = subprocess.run(git_args, capture_output=True, text=True, encoding="utf-8")
    if result.returncode != 0:
        _die(f"Error: git diff failed:\n{result.stderr.strip()}")
    diff_text = result.stdout
    if not diff_text.strip():
        hint = "staged" if staged else "unstaged"
        _die(f"Error: git diff produced no output (no {hint} changes). Check --repo.")

    nb = _load(notebook)
    cells = nb["cells"]
    n = _resolve_cell(cells, cell_n)
    src = _cell_source(cells[n])

    replaced = False

    def _replace_first(m: re.Match) -> str:
        nonlocal replaced
        # Match a ```diff fence (placeholder or real content) or a fence that already
        # contains a real diff (starts with "diff --git" or "--- ").
        fence_tag = m.group(1).rstrip("\n")
        is_diff_fence = fence_tag.endswith("diff") or m.group(2).lstrip().startswith(("diff --git", "--- "))
        if not replaced and is_diff_fence:
            replaced = True
            return m.group(1) + diff_text + "```"
        return m.group(0)

    new_src = re.sub(r"(```[^\n]*\n)(.*?)```", _replace_first, src, flags=re.DOTALL)
    if not replaced:
        _die(f"Error: cell {n} has no diff fence to replace.")

    cells[n]["source"] = _to_source_lines(new_src)
    _save(notebook, nb)
    print(f"Cell {n} patch fence updated ({diff_text.count(chr(10))} lines).", file=sys.stderr)


def cmd_export_html(notebook: str, output_dir: str | None) -> None:
    nb = _load(notebook)
    nb_path = Path(notebook).resolve()

    # Cells with source_hidden=true (JupyterLab metadata) need a tag so that
    # nbconvert knows to omit their source from HTML output.
    hidden_tag = "nb-source-hidden"
    hidden_cells = [
        cell for cell in nb["cells"]
        if cell.get("metadata", {}).get("jupyter", {}).get("source_hidden")
    ]
    tmp = None
    try:
        if hidden_cells:
            for cell in hidden_cells:
                tags = cell.setdefault("metadata", {}).setdefault("tags", [])
                if hidden_tag not in tags:
                    tags.append(hidden_tag)
            with tempfile.NamedTemporaryFile(
                mode="w", suffix=".ipynb", delete=False,
                dir=nb_path.parent, encoding="utf-8",
            ) as f:
                json.dump(nb, f, indent=1, ensure_ascii=False)
                f.write("\n")
                tmp = f.name
            src = tmp
        else:
            src = str(nb_path)

        try:
            import nbconvert  # noqa: F401 — available when run via `uv run nb.py`
            jupyter_cmd = [sys.executable, "-m", "jupyter"]
        except ImportError:
            jupyter_cmd = [_UV, "run"] + _NB_PACKAGES + ["jupyter"]
        args = jupyter_cmd + [
            "nbconvert",
            "--to", "html",
        ]
        if hidden_cells:
            args += [
                f'--TagRemovePreprocessor.remove_input_tags=["{hidden_tag}"]',
                "--output", nb_path.stem,
                "--output-dir", str(output_dir or nb_path.parent),
            ]
        elif output_dir:
            args += ["--output-dir", output_dir]
        args.append(src)
        sys.exit(subprocess.run(args).returncode)
    finally:
        if tmp:
            os.unlink(tmp)


def cmd_execute(notebook: str) -> None:
    nb_path = str(Path(notebook).resolve())
    try:
        import nbconvert  # noqa: F401 — available when run via `uv run nb.py`
        args = [sys.executable, "-m", "jupyter", "nbconvert",
                "--to", "notebook", "--execute", "--inplace", nb_path]
    except ImportError:
        args = [_UV, "run"] + _NB_PACKAGES + [
            "jupyter", "nbconvert", "--to", "notebook", "--execute", "--inplace", nb_path]
    sys.exit(subprocess.run(args).returncode)


def cmd_serve(notebook: str) -> None:
    nb_path = str(Path(notebook).resolve())
    try:
        import jupyterlab  # noqa: F401 — available when run via `uv run nb.py`
        args = [sys.executable, "-m", "jupyter", "lab", nb_path]
    except ImportError:
        args = [_UV, "run", "--with", "jupyterlab"] + _NB_PACKAGES + [
            "jupyter", "lab", nb_path]
    sys.exit(subprocess.run(args).returncode)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Notebook helper CLI for JetBrains experiment notebooks.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", metavar="COMMAND")
    sub.required = True

    p = sub.add_parser("list-cells", help="Print index, type, and first line of every cell.")
    p.add_argument("notebook")

    p = sub.add_parser("get-cell", help="Print the source of cell N (0-based) to stdout.")
    p.add_argument("notebook")
    p.add_argument("n", type=int)

    p = sub.add_parser("set-cell", help="Replace cell N source with FILE content ('-' reads from stdin).")
    p.add_argument("notebook")
    p.add_argument("n", type=int)
    p.add_argument("file")

    p = sub.add_parser("insert-cell", help="Insert a new cell at position N, shifting later cells down.")
    p.add_argument("notebook")
    p.add_argument("n", type=int)
    p.add_argument("type", choices=["code", "markdown"])
    p.add_argument("file", nargs="?", default="-", help="Source file ('-' reads from stdin, default).")

    p = sub.add_parser("delete-cell", help="Delete cell N.")
    p.add_argument("notebook")
    p.add_argument("n", type=int)

    p = sub.add_parser(
        "apply-patch",
        help="Extract diff block(s) from cell N (default: last) and apply to the repo.",
        description=(
            "Extract unified-diff blocks from cell N (default: last cell) and apply "
            "them to the git repo. Skips already-applied patches. Falls back to "
            "--3way merge when context lines do not match the current HEAD."
        ),
    )
    p.add_argument("notebook")
    p.add_argument("n", type=int, nargs="?", default=None, metavar="N")
    p.add_argument("--repo", default=None, metavar="DIR",
                   help="Git repo root to apply the patch in (default: current working directory).")

    p = sub.add_parser(
        "update-patch",
        help="Replace the diff fence in cell N (default: last) with the current git diff.",
        description=(
            "Read the current git diff and write it into the first diff fence of "
            "cell N (default: last cell). Useful after iterating on a patch — "
            "restage the files and run this to bake the result back into the notebook."
        ),
    )
    p.add_argument("notebook")
    p.add_argument("n", type=int, nargs="?", default=None, metavar="N")
    p.add_argument("--repo", default=None, metavar="DIR",
                   help="Git repo root to read the diff from (default: current working directory).")
    p.add_argument("--staged", action="store_true",
                   help="Use git diff --cached (staged changes only) instead of the working-tree diff.")

    p = sub.add_parser(
        "collapse-cells",
        help="Hide source of all code cells except config cells (default: cell 1).",
        description=(
            "Set source_hidden=true on every code cell except those tagged 'nb:visible' "
            "or listed via --keep. When no cell carries the tag, --keep defaults to [1] "
            "(the standard config cell). Run expand-cells to reverse."
        ),
    )
    p.add_argument("notebook")
    p.add_argument("--keep", type=int, nargs="+", default=[1], metavar="N",
                   help="Cell indices to keep visible when no nb:visible tag is found (default: 1).")

    p = sub.add_parser(
        "expand-cells",
        help="Show source of all code cells (reverse of collapse-cells).",
    )
    p.add_argument("notebook")

    p = sub.add_parser("execute", help="Run all cells in place (uv + nbconvert, overwrites outputs).")
    p.add_argument("notebook")

    p = sub.add_parser("export-html", help="Export the notebook to a standalone HTML file.")
    p.add_argument("notebook")
    p.add_argument("-o", "--output-dir", default=None, metavar="DIR",
                   help="Directory to write the HTML file into (default: same directory as the notebook).")

    p = sub.add_parser("serve", help="Start JupyterLab for interactive editing (opens browser by default).")
    p.add_argument("notebook")

    args = parser.parse_args()
    repo = getattr(args, "repo", None) or str(Path.cwd())

    if args.cmd == "list-cells":
        cmd_list_cells(args.notebook)
    elif args.cmd == "get-cell":
        cmd_get_cell(args.notebook, args.n)
    elif args.cmd == "set-cell":
        cmd_set_cell(args.notebook, args.n, args.file)
    elif args.cmd == "insert-cell":
        cmd_insert_cell(args.notebook, args.n, args.type, args.file)
    elif args.cmd == "delete-cell":
        cmd_delete_cell(args.notebook, args.n)
    elif args.cmd == "apply-patch":
        cmd_apply_patch(args.notebook, args.n, repo)
    elif args.cmd == "update-patch":
        cmd_update_patch(args.notebook, args.n, repo, args.staged)
    elif args.cmd == "collapse-cells":
        cmd_collapse_cells(args.notebook, args.keep)
    elif args.cmd == "expand-cells":
        cmd_expand_cells(args.notebook)
    elif args.cmd == "execute":
        cmd_execute(args.notebook)
    elif args.cmd == "export-html":
        cmd_export_html(args.notebook, args.output_dir)
    elif args.cmd == "serve":
        cmd_serve(args.notebook)


if __name__ == "__main__":
    main()
