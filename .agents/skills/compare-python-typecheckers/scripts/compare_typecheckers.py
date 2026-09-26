#!/usr/bin/env python3
"""Run a Python file or snippet through several third-party type checkers and
collate their output into a single Markdown report.

Checkers (each fetched on demand with `uvx`, so no pre-install is needed; the
network is used on first run): ty, pyrefly, basedpyright, mypy, zuban.

Examples:
    compare_typecheckers.py test.py
    compare_typecheckers.py -c 'x: int = "a"'
    compare_typecheckers.py test.py --tools ty,mypy -o report.md
"""
#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path

# name -> argv prefix; the target file path is appended. Order = report order.
# Quirks worth remembering: basedpyright takes the file positionally (no `check`
# subcommand); zuban emits mypy-compatible output.
CHECKERS: dict[str, list[str]] = {
    "ty": ["uvx", "ty", "check"],
    "pyrefly": ["uvx", "pyrefly", "check"],
    "basedpyright": ["uvx", "basedpyright"],
    "mypy": ["uvx", "mypy"],
    "zuban": ["uvx", "zuban", "check"],
}


def run_checker(name: str, target: Path, timeout: float) -> dict:
    # Run from the file's own directory with a relative name. Some checkers
    # (notably zuban) reject an absolute path outside the cwd ("No Python files
    # found to check"); this also keeps the displayed command readable and lets
    # tools pick up a co-located config.
    cmd = CHECKERS[name] + [target.name]
    start = time.monotonic()
    try:
        proc = subprocess.run(cmd, cwd=str(target.parent), capture_output=True, text=True, timeout=timeout)
        out = (proc.stdout or "") + (proc.stderr or "")
        code: int | None = proc.returncode
        status = "clean" if code == 0 else "issues"
    except subprocess.TimeoutExpired:
        out, code, status = f"(timed out after {timeout:.0f}s)", None, "timeout"
    except FileNotFoundError:
        out, code, status = "(uvx not found on PATH)", None, "error"
    return {
        "name": name,
        "cmd": " ".join(cmd),
        "out": out.strip(),
        "code": code,
        "status": status,
        "dur": time.monotonic() - start,
    }


def build_report(target: Path, results: list[dict], source: str | None, now: str) -> str:
    lines: list[str] = [f"# Type-checker comparison — `{target.name}`", "", f"_Generated {now}_", ""]
    if source is not None:
        lines += ["Source under test:", "", "```python", source.rstrip("\n"), "```", ""]
    lines += [
        "## Summary",
        "",
        "| Checker | Command | Exit | Verdict | Time |",
        "|---------|---------|------|---------|------|",
    ]
    for r in results:
        code = "-" if r["code"] is None else str(r["code"])
        lines.append(f"| {r['name']} | `{r['cmd']}` | {code} | {r['status']} | {r['dur']:.1f}s |")
    lines += [
        "",
        "> Exit 0 = nothing flagged; non-zero = the checker reported an issue or "
        "failed to run. Output formats differ per tool — read each section below.",
        "",
    ]
    for r in results:
        lines += [f"## {r['name']} — `{r['cmd']}`", "", "```", r["out"] or "(no output)", "```", ""]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description="Compare Python type checkers (ty, pyrefly, basedpyright, mypy, zuban) via uvx.",
    )
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("file", nargs="?", help="Python file to check")
    src.add_argument("-c", "--code", help="inline snippet to check (written to a temp file)")
    p.add_argument("-t", "--tools", help=f"comma-separated subset of: {', '.join(CHECKERS)}")
    p.add_argument("-o", "--output", help="write the report to this path instead of stdout")
    p.add_argument("--timeout", type=float, default=180.0, help="per-checker timeout in seconds (default 180)")
    args = p.parse_args(argv)

    # Reports and progress lines contain non-ASCII (em dashes, ellipsis); force
    # UTF-8 so printing to a non-UTF-8 console (e.g. Windows cp1252) can't crash.
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")

    if shutil.which("uvx") is None:
        print("error: `uvx` not found on PATH — install uv (https://docs.astral.sh/uv/).", file=sys.stderr)
        return 2

    tmp_dir: str | None = None
    if args.code is not None:
        tmp_dir = tempfile.mkdtemp(prefix="typecheck_")
        target = Path(tmp_dir) / "snippet.py"
        target.write_text(args.code if args.code.endswith("\n") else args.code + "\n", encoding="utf-8")
        source_text = args.code
    else:
        target = Path(args.file)
        if not target.is_file():
            print(f"error: no such file: {target}", file=sys.stderr)
            return 2
        target = target.resolve()
        try:
            source_text = target.read_text(encoding="utf-8")
        except OSError:
            source_text = None

    names = list(CHECKERS)
    if args.tools:
        names = [t.strip() for t in args.tools.split(",") if t.strip()]
        unknown = [t for t in names if t not in CHECKERS]
        if unknown:
            print(f"error: unknown tool(s): {', '.join(unknown)} (known: {', '.join(CHECKERS)})", file=sys.stderr)
            return 2

    results = []
    for name in names:
        print(f"running {name} …", file=sys.stderr)
        results.append(run_checker(name, target, args.timeout))

    report = build_report(target, results, source_text, f"{datetime.now():%Y-%m-%d %H:%M:%S}")
    if args.output:
        Path(args.output).write_text(report, encoding="utf-8")
        print(f"wrote {args.output}", file=sys.stderr)
    else:
        print(report)

    if tmp_dir is not None:
        shutil.rmtree(tmp_dir, ignore_errors=True)
    # Non-zero only on harness failure, not on findings: a clean exit lets callers
    # treat "report produced" as success regardless of what the checkers found.
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
