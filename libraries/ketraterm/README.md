# KetraTerm dependencies

The terminal plugin uses KetraTerm `1.0.1` from Maven Central.
The [JPS module](intellij.libraries.ketraterm.iml) declares the dependencies. [Bazel](../../lib/MODULE.bazel) pins each jar by release version and SHA-256.
The module uses the platform versions of Kotlin, coroutines core, and immutable collections.

To try KetraTerm, follow the [IDE launch instructions](../../plugins/terminal/KETRATERM.md#run).

## Tests

Run the view and session tests from the repository root.

```powershell
.\tools\bt.cmd --module intellij.terminal.tests --filter com.intellij.terminal.tests.reworked.frontend.ketraterm
.\tools\bt.cmd --module intellij.terminal.tests --filter com.intellij.terminal.tests.reworked.frontend.session.ketraterm
```

For shell tests, use `--test_env=KETRATERM_TEST_SHELL=<path-to-pwsh>` if Bazel cannot find PowerShell.
To include the Neovim test, pass `--test_env=KETRATERM_TEST_TUI=<path-to-nvim>`.
