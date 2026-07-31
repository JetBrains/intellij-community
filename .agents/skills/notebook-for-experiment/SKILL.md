---
name: notebook-for-experiment
description: Create reproducible Jupyter notebooks for performance, profiling, or benchmark experiments.
---

# Notebook for experiment

## Tooling

### Installing uv

`uv` is the recommended tool for running notebooks — it requires no global Jupyter installation,
pins exact dependency versions per invocation, and keeps each invocation's environment isolated.
There are no conflicts with system Python or existing virtualenvs.

Install on macOS / Linux:
```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Install on Windows (PowerShell):
```powershell
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"
```

Inside the IntelliJ monorepo, use the pinned wrapper instead of a system `uv`.
It downloads and caches the right version automatically:

```bash
# Full monorepo layout (project root contains community/):
./community/tools/uv.cmd run ...

# Community-only checkout (project root IS community/):
./tools/uv.cmd run ...
```

### Notebook helper tool

`${CLAUDE_SKILL_DIR}/scripts/nb.py` is a small CLI that covers the
most common notebook operations without requiring a running Jupyter server:

```bash
# List all cells (index, type, first line)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py list-cells notebook.ipynb

# Print source of cell N (0-based)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py get-cell notebook.ipynb 3

# Replace cell N with content from stdin (use a file path instead of '-' to read from a file)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py set-cell notebook.ipynb 3 -

# Insert a new cell at position N (shifts later cells down); reads from stdin or a file
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py insert-cell notebook.ipynb 6 markdown < description.md
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py insert-cell notebook.ipynb 11 code snippet.py

# Delete cell N
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py delete-cell notebook.ipynb 5

# Collapse all code cells except the config cell (cell 1) so the notebook
# opens with only the editable config visible; use expand-cells to reverse
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py collapse-cells notebook.ipynb
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py expand-cells notebook.ipynb

# Keep a non-standard cell visible by tagging it nb:visible, or with --keep:
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py collapse-cells notebook.ipynb --keep 1 4

# Apply the patch(es) embedded in the last cell to a git repo
# --repo is required when the notebook and repo are on different paths
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py apply-patch notebook.ipynb --repo /path/to/repo

# Apply the patch(es) embedded in a specific cell
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py apply-patch notebook.ipynb 12 --repo /path/to/repo

# Bake the current git diff back into the diff fence of the last cell
# (the inverse of apply-patch — use after iterating on the patch)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py update-patch notebook.ipynb --repo /path/to/repo
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py update-patch notebook.ipynb --repo /path/to/repo --staged

# Execute all cells in place (overwrites outputs)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py execute notebook.ipynb

# Export to HTML (writes alongside the notebook; use -o for a custom directory).
# Cells collapsed via collapse-cells have their source omitted in the HTML output.
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py export-html notebook.ipynb
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py export-html notebook.ipynb -o out/

# Start JupyterLab for interactive editing (opens in browser)
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py serve notebook.ipynb
```

Always invoke nb.py via `uv run` — this ensures a compatible Python is used.
nb.py carries inline dependency metadata (PEP 723), so uv installs the required packages
(nbconvert, jupyter-client, ipykernel, numpy, pandas, matplotlib, plotly) automatically
for commands that need them.

If system `uv` is not available, use the pinned monorepo wrapper instead:

```bash
# Full monorepo layout:
./community/tools/uv.cmd run ${CLAUDE_SKILL_DIR}/scripts/nb.py list-cells notebook.ipynb

# Community-only checkout:
./tools/uv.cmd run ${CLAUDE_SKILL_DIR}/scripts/nb.py list-cells notebook.ipynb
```

### Passing complex arguments on Windows

PowerShell and cmd.exe handle escaping poorly for arguments containing backslashes, spaces, or
special characters (e.g. JVM options with UNC paths). Store the value in an environment variable
first to sidestep the problem.

Bash:
```bash
export WSL_PROJECT='\\wsl.localhost\Ubuntu-24.04\home\yourname\idea'
./tests.cmd --jvm-option "-Dijent.wsl.project.root=$WSL_PROJECT"
```

PowerShell:
```powershell
$env:WSL_PROJECT = '\\wsl.localhost\Ubuntu-24.04\home\yourname\idea'
./tests.cmd --jvm-option "-Dijent.wsl.project.root=$env:WSL_PROJECT"
```

The same technique applies for any argument that would be hard to inline-escape: paths, output
directories, or flags that differ per engineer. The Kotlin side can read the value via
`System.getProperty("...")` or `System.getenv("...")`.

## Cell visibility

By convention, notebooks should open with all boilerplate code collapsed so the
reader only sees the config cell they need to edit. Use `collapse-cells` after
finishing the notebook structure:

```bash
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py collapse-cells notebook.ipynb
```

This hides all code cells except cell 1 (the standard config cell). To mark a
different cell as permanently visible, add the tag `nb:visible` to its metadata:

```python
# In a one-off update script:
nb["cells"][4]["metadata"].setdefault("tags", []).append("nb:visible")
```

When any code cell carries `nb:visible`, the tag takes precedence over `--keep`.

After collapsing, open the notebook in the browser to verify the layout before committing:

```bash
uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py serve notebook.ipynb
```

## Standard cell layout

| # | Type | Purpose |
|---|------|---------|
| 0 | markdown | Title; what is measured; exact shell command(s) to collect data |
| 1 | code | Config — only user-editable variables (e.g. `repo_root = Path(r'...')`) |
| 2 | code | Machine info (host, OS, CPU, RAM) |
| 3 | code | Imports + data loading |
| 4..N | code | Analysis — parse, stats, plots, tables |
| last | markdown | Full patch(es) needed to reproduce the data, as fenced diff blocks |

## Reproducibility

Every notebook must be self-contained enough for a different engineer on a different machine to re-run it and get the same results.

**Required elements:**

- **Pinned commit**: state the exact git hash under which results were collected (in the title cell or a comment in the config cell).
  The commit must belong to a stable branch — `origin/master` or a release branch (three-digit major version and maybe a minor version after a dot, e.g. `origin/261`, `origin/261.12345`).
  Feature branches are deleted after merging, so a hash reachable only from a feature branch becomes inaccessible to anyone trying to reproduce the results.
- **Full patch in last cell**: embed the exact diff as a fenced block.
  Bash / macOS / Linux:
  ```bash
  git diff $(git merge-base <stable branch> <hash>) <hash>
  ```
  PowerShell:
  ```powershell
  git diff (git merge-base <stable branch> <hash>) <hash>
  ```
  If an instrumentation patch was applied separately (not merged), include it too.
- **Exact run command**: the title cell must contain the complete command including module and test class FQN.

## Test stability

Before writing the notebook, verify the test workload is deterministic and noise-free. **Ask the user if uncertain.** Red flags:

| Risk | Mitigation |
|------|------------|
| Index caches reused across runs | `preserveSystemDir = false` (IDE Starter default) |
| Pre-built / shared indexes downloaded | `.setSharedIndexesDownload(false)` |
| Background telemetry uploads | `.disableReportingStatisticsToProduction()`, `.disableFusSendingOnIdeClose()` |
| Test runs on wrong filesystem | Verify the actual runtime path (e.g. WSL or Docker path vs Windows host path). For WSL/Docker tests, check that `resolvedProjectHome` points inside the target environment. A common pitfall: hook APIs that return a new context but the caller discards the return value, silently keeping the old path. |
| Non-deterministic algorithms | Verify fixed seeds, disabled sampling, etc. |
| Competing background processes | Identify and disable anything that shares the measured resource. |

If a test result depends on any random or session-specific factor, **ask the user** before proceeding.

## Test design

The fewer steps a user needs to collect data, the better. The ideal: apply one patch, run one
command, open the notebook.

**Aim for a single test invocation.** Put all measurements in one test class. If measurements
share setup (e.g. opening and indexing a project), run them in sequence inside one test method,
or as `@Order`-annotated methods that always run together as a group.

**Use a single patch.** Combine instrumentation and test changes into one diff. Two patches
double the preparation steps for anyone trying to reproduce the results.

**Anti-pattern:** a notebook that requires running an indexing test and a rename test as separate
invocations (different `--module` flags, different log files to locate) forces a two-step recipe.
Merge the measurements into one test class so a single command generates all data.

**Verify that the experiment code compiles** before embedding the patch in the notebook.
Run `./tests.cmd --module <module> --test <FQN>` — Bazel compilation runs as part of the
test launch even if the test fails at runtime. Fix any compilation errors before the diff
goes into the patch cell.

## Machine info cell

See [reference/machine-info.md](reference/machine-info.md) for a stdlib-only implementation that works on Windows, macOS, and Linux.

## Finding log files

See [reference/log-files.md](reference/log-files.md) for patterns covering IDE Starter tests and regular (non-Starter) tests.

## Availability guards

Some notebooks measure multiple configurations, and a user may
have run only one of them. In those cases the data-loading cell should set a boolean flag and
every subsequent analysis cell that depends on that data should check it before running.
This lets `execute` (or "Run All") complete without errors even when only partial data exists.

For **two variants** the pattern is straightforward:

```python
# In the data-loading cell — set flags for each optional configuration:
try:
    async_log = find_log('my-test-async')
    _async_sizes = parse_sizes(async_log)
except FileNotFoundError as e:
    print(f'Skipping async mode: {e}')
    _async_sizes = []

_async_ok = bool(_async_sizes)

# In each analysis cell that uses async data:
if not _async_ok:
    print('No async data — run the async variant of the test first.')
else:
    # ... analysis code ...
```

For **three or more variants**, keep the loading block uniform and collect results into a
dict so analysis cells stay readable regardless of how many variants exist:

```python
# In the data-loading cell:
_VARIANTS = ['baseline', 'nagle', 'nodelay']
_data: dict[str, list] = {}

for _variant in _VARIANTS:
    try:
        _log = find_log(f'my-test-{_variant}')
        _data[_variant] = parse_sizes(_log)
        print(f'{_variant}: {len(_data[_variant])} samples')
    except FileNotFoundError as e:
        print(f'Skipping {_variant}: {e}')
        _data[_variant] = []

# In each analysis cell — iterate only over variants that have data:
_available = {k: v for k, v in _data.items() if v}
if not _available:
    print('No data available — run at least one test variant first.')
else:
    for variant, sizes in _available.items():
        # ... per-variant analysis ...
        pass
```

This scales to any number of variants without duplicating the guard logic.

Never `raise` or `sys.exit()` inside an analysis cell — that aborts the entire notebook execution.
The `find_log` function is intentionally different: it raises because it guards a hard prerequisite
(the test must have run), while availability guards handle optional configurations.

## Updating an existing notebook

To change cell content without running a full Jupyter server:

1. **For a single cell** — use `nb.py set-cell` with `-` to read from stdin:

   Bash (macOS / Linux / Git Bash):
   ```bash
   uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py set-cell notebook.ipynb 3 - <<'EOF'
   import pandas as pd
   df = pd.read_csv(log_path)
   EOF
   ```

   PowerShell:
   ```powershell
   @"
   import pandas as pd
   df = pd.read_csv(log_path)
   "@ | uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py set-cell notebook.ipynb 3 -
   ```

   Passing a file path instead of `-` is also supported when the content is already on disk.

   `set-cell` preserves the existing cell type — replacing a markdown cell with a `.md`
   file leaves it as markdown; replacing a code cell leaves it as code. Outputs are
   cleared only for code cells (stale outputs from old code would be misleading).

2. **For multi-cell edits** — write a temporary Python script that manipulates the JSON directly,
   run it, then delete the script:
   ```python
   import json, pathlib

   nb_path = pathlib.Path('notebook.ipynb')
   nb = json.loads(nb_path.read_text(encoding='utf-8'))

   def set_src(cells, n, text):
       lines = text.splitlines(keepends=True)
       if lines and lines[-1].endswith('\n'):
           lines[-1] = lines[-1][:-1]
       cells[n]['source'] = lines

   set_src(nb['cells'], 1, 'repo_root = ...\n')
   set_src(nb['cells'], 3, 'import ...\n')

   nb_path.write_text(json.dumps(nb, indent=1, ensure_ascii=False) + '\n', encoding='utf-8')
   ```

3. **After modifying experiment source files** (Kotlin, Java, Rust, etc.) that are part
   of the patch — run `update-patch` to bake the updated diff back into the last cell:
   ```bash
   uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py update-patch notebook.ipynb \
       --repo /path/to/repo
   # or, if you have staged the changes instead:
   uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py update-patch notebook.ipynb \
       --repo /path/to/repo --staged
   ```
   Do this after every iteration on the experiment code so the patch cell always
   reflects exactly what was applied when the data was collected. Forgetting this step
   is a common source of stale patches in committed notebooks.

4. **Re-execute** to refresh all outputs:
   ```bash
   uv run ${CLAUDE_SKILL_DIR}/scripts/nb.py execute notebook.ipynb
   ```

The notebook file itself is the artifact to commit; keep any update scripts temporary.

## Cell content guidelines

### Static text: use Markdown, not `print()`

A code cell that only calls `print("...")` or `print("""...""")` with literal strings should
be a Markdown cell instead. Markdown cells render with proper headings, bold, tables, and
links; `print()` output is plain monospace with no formatting.

**Do** — markdown cell:
```
## Key finding

The root cause is …
```

**Don't** — code cell:
```python
print("""Key finding\n\nThe root cause is …""")
```

Only use `print()` for values computed at runtime (log paths, parsed numbers, verdicts that
depend on data).

### Measurements: add visual plots

Every cell that produces a table of measured values should also (or instead) produce a
`matplotlib` figure. Prefer:

- **Bar charts** for comparing values across configurations or time points.
- **Histograms** for distributions (e.g. batch-size counts).
- **Pie / stacked-bar charts** for proportions (e.g. special-channel vs gRPC bytes).

Use `plt.show()` so outputs are embedded in the notebook.  When data for a configuration is
unavailable (guarded by an `_ok` flag), skip its bars rather than crashing.

### Tabular data: use pandas, not manual formatting

When an analysis cell produces a table — comparison results, per-variant stats, ranked lists — use
a `pandas` DataFrame instead of building ASCII tables with `print()` and string formatting.
Jupyter renders DataFrames as HTML automatically; the output is sortable, readable, and
copy-paste friendly.

**Do** — DataFrame output:
```python
import pandas as pd

rows = [
    {"variant": "baseline", "p50_ms": 120, "p99_ms": 340},
    {"variant": "nagle",    "p50_ms": 118, "p99_ms": 310},
    {"variant": "nodelay",  "p50_ms":  95, "p99_ms": 270},
]
df = pd.DataFrame(rows)
display(df)
```

**Don't** — manual ASCII table:
```python
print(f"{'variant':<10} {'p50_ms':>8} {'p99_ms':>8}")
for r in rows:
    print(f"{r['variant']:<10} {r['p50_ms']:>8} {r['p99_ms']:>8}")
```

Use pandas for the full data lifecycle inside notebook cells:

- **Loading:** `pd.read_csv(log_path)` or construct from parsed dicts/lists.
- **Transforming:** `df.groupby(...).agg(...)`, `df.assign(delta=df.after - df.before)`.
- **Displaying:** `display(df)` (HTML table) or `display(df.describe())` for quick stats.
- **Feeding plots:** `df.plot.bar(x="variant", y=["p50_ms", "p99_ms"])` integrates directly with matplotlib.

Declare `pandas` in the imports cell (cell 3) so it is available to all subsequent analysis cells.

## Anti-patterns

- Hardcoded absolute log paths — breaks on every other machine.
- No commit hash or patch — results cannot be reproduced.
- Tests that reuse index caches or download shared indexes — results vary between runs.
- Tests that run on the wrong filesystem silently (WSL/Docker path bugs) — data measures the wrong thing.
- `raise` / `sys.exit()` on missing data — breaks "run all cells".
- Measuring a workload with an unverified random or session-specific factor without asking the user.
