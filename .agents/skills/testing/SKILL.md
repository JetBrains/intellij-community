---
name: testing
description: Run or troubleshoot IntelliJ tests with `bt.cmd` or `tests.cmd`.
---

# Testing Guide for IntelliJ IDEA

## Quick Start

Run the tests of a module with bt first:

```bash
./community/tools/bt.cmd --module <module> --filter <FQN>
```

- `--module`: the JPS module that holds the test classes. Always use the module of the test itself. The module name is the name of the `.iml` file in the test directory, without the extension.
- `--filter`: an FQN, `FQN#method`, or an all-lowercase package. A package value runs all the test classes in that package.

In a community checkout, the wrapper is `./tools/bt.cmd`.

```bash
# Single test class (FQN)
./community/tools/bt.cmd --module intellij.platform.util.tests \
    --filter com.intellij.openapi.util.io.FileUtilLightTest

# Specific test method
./community/tools/bt.cmd --module intellij.platform.util.tests \
    --filter 'com.intellij.openapi.util.io.FileUtilLightTest#isAncestor'

# All test classes of a package
./community/tools/bt.cmd --module intellij.platform.util.tests \
    --filter com.intellij.openapi.util.io
```

bt runs a module when the migrated list names it, or when a `bt.json` area owns its directory. The [bt README](../../../tools/bt/README.md#modules) gives the resolution rules.

### bt options

| Option | Effect |
|--------|--------|
| `--filter <value>` | Runs one class, one method, or one package of the target. |
| `--no-cache` | Runs the tests again when Bazel has a cached result. |
| `--dry-run` | Prints the resolved label, the filter, and the Bazel command. Runs nothing. |
| `--json` | Writes one JSON object to stdout and nothing else. |
| `--host <host>` | Runs the committed HEAD on another machine of the user through `bd-host` of Branch Deck. The `bd-host` skill (`bd-host skill install`) gives the rules. |
| `-- --flaky_test_attempts=3` | Runs a failed test up to 3 times. `tests.cmd` uses `-Dintellij.build.test.attempt.count=3` for this. |
| `-- --test_arg=--jvm_flag=-Dkey=value` | Sets a system property in the test JVM. `tests.cmd` uses `-Dpass.key=value` for this. |
| `-- --test_arg=--jvm_flag=-Xmx8g` | Sets the heap of the test JVM. `tests.cmd` uses `-Dintellij.build.test.jvm.memory.options=-Xmx8g` for this. |

bt gives all arguments after `--` to Bazel without a change. Run `./community/tools/bt.cmd --help` for all selectors and options. To attach a debugger, use `tests.cmd`. The section [Additional JVM options](#additional-jvm-options) shows the flags.

| Exit code | Meaning |
|-----------|---------|
| 0 | All tests passed. |
| 2 | Usage error, or bt refuses the module. |
| 3 | Tests failed. |
| 4 | Zero tests ran. |
| 5 | The build failed before the tests. |
| 6 | Infrastructure failure. |

### The check record and `bt build`

A green bt run in a linked work tree, such as a Branch Deck lease, records the tree that it saw: one line in `bd-checks.jsonl` of the git directory. `bd lease pick` reads the record. A repository can set `pick_requires_check` in `.branchdeck.toml`, and then the pick refuses a lease whose tree has no green build or test. A run during which the files changed records nothing. A red run records nothing.

Check the last tree with bt, not with a raw `./bazel.cmd build`. `bazel.cmd` gives the process to bazelisk, so it leaves no record. Commit or delete scratch files before the last check: a record of local changes counts only for a commit of the same files, and bt names those files on the stderr.

```bash
./community/tools/bt.cmd build //plugins/air/...     # ./bazel.cmd build -k, one digest line, and the record
./community/tools/bt.cmd build //plugins/air/... --host studio
```

`bt build` exits 0 for a green build, 5 for a build failure, and 6 for an infrastructure failure. A `--host` run records the tree of the commit that ran on the host. The [bt README](../../../tools/bt/README.md#the-check-record) gives the record format.

### When bt refuses the module

bt refuses a module whose tests do not run under Bazel yet. It exits with code 2 and prints the `tests.cmd` command. Run that command:

```bash
./tests.cmd --module <module> --test <pattern>
```

- `--module` — the JPS module containing the test classes (always use the test's own module). To find the module name, look at the `.iml` file in the test's directory — the module name is the `.iml` filename without the extension.
- `--test` — FQN, wildcard pattern, or FQN#methodName

```bash
# Single test class (FQN)
./tests.cmd --module intellij.cidr.compiler.custom.tests \
            --test com.intellij.cidr.compiler.custom.CidrCustomCompilerReadTest

# Wildcard pattern
./tests.cmd --module intellij.goland.tests \
            --test com.goide.comments.*Test

# Specific test method (wildcards cannot be used with #)
./tests.cmd --module intellij.cidr.compiler.custom.tests \
            --test com.intellij.cidr.compiler.custom.CidrCustomCompilerReadTest#testSingleDefine

# Multiple (semicolon-separated)
./tests.cmd --module intellij.platform.build.tests \
            --test org.jetbrains.intellij.build.TestSelectorsTest#class selector;org.jetbrains.intellij.build.FileSetTest
```

**Simple class names like `MyTest` do NOT work** — always use FQN or wildcard (`*MyTest`).

### Why simple names fail

Patterns are transformed (`*` → `.*`, `.` → `\.`) and matched against the fully qualified class name using `Pattern.matches()` (full-string match):

| Pattern | Regex | Matches `org.example.MyTest`? |
|---------|-------|-------------------------------|
| `MyTest` | `MyTest` | NO — doesn't cover the package prefix |
| `*MyTest` | `.*MyTest` | YES |
| `org.example.MyTest` | `org\.example\.MyTest` | YES |
| `org.example.*` | `org\.example\..*` | YES |

### Community tests

Use `community/tests.cmd` for tests that belong to community-only modules:

```bash
./community/tests.cmd --module <module> --test <pattern>
```

### API checks via Bazel

In an Ultimate checkout, run `ApiCheckTest` directly through its Bazel test target. For a focused check, pass one or more comma-separated module names:

```bash
./bazel.cmd test //tests/ideaProjectStructure:projectStructureTests_test \
  --test_filter=com.intellij.ideaProjectStructure.api.ApiCheckTest \
  --test_arg=--jvm_flag=-Dapi.dump.test.modules.to.check=<module>[,<module>...] \
  --test_output=summary \
  --test_summary=detailed
```

Omit `--test_arg` to check all modules. Do not use `bazel run` for this target: it streams noisy test output and does not provide Bazel's test summary.

This bt command is the same run with a digest:

```bash
./community/tools/bt.cmd //tests/ideaProjectStructure:projectStructureTests_test \
  --filter com.intellij.ideaProjectStructure.api.ApiCheckTest \
  -- --test_arg=--jvm_flag=-Dapi.dump.test.modules.to.check=<module>[,<module>...]
```

On failure, keep agent context small by extracting the failing dynamic-test names and messages from Bazel's JUnit report instead of printing the full `test.log`:

```bash
xmllint --xpath '//testcase[failure or error]/@name | //testcase[failure or error]/*[self::failure or self::error]/@message' \
  out/bazel-testlogs/tests/ideaProjectStructure/projectStructureTests_test/test.xml
```

### Product layout and packaging changes

When changing `ProductProperties`, `productImplementationModules`, product content descriptors, plugin/module-set packaging, or generated product layout XML, also run:

```bash
./community/tools/bt.cmd //build:all-products-packaging_test
```

The target pins the one class `AllProductsPackagingTest` and is tagged `manual`, so no `--filter` is needed, and no lane or wildcard pattern runs it. bt prints the digest, and Bazel writes `out/bazel-testlogs/build/all-products-packaging_test/test.xml`. The raw form is `./bazel.cmd test //build:all-products-packaging_test`.

### Windows PowerShell note

When running `tests.cmd` from PowerShell, pass JVM `-D...` arguments via stop-parsing mode to avoid argument mangling:

```powershell
./tests.cmd --% -Dintellij.build.test.patterns=com.example.MyTest
```

Without `--%`, PowerShell can alter `-D...` arguments before they reach `tests.cmd`, which may lead to errors like `Could not find or load main class ...`.

## Separate Bazel Modules

Some parts of the repository are standalone Bazel modules and must not use `tests.cmd` or `community/tests.cmd`. The tests of a module that matches a pattern in the migrated list run only under Bazel. The migrated list is `community/build/bazel-migrated-test-modules.txt`, or `build/bazel-migrated-test-modules.txt` in a community checkout. `tests.cmd` refuses such a module with an error that names the pattern. Run the module with bt:

```bash
./community/tools/bt.cmd --module <module> --filter <FQN>
```

When you have the Bazel label of the target, `./community/tools/bt.cmd <label> --filter <FQN>` runs the same target.

### `community/platform/build-scripts/bazel`

- This directory is a separate Bazel module.
- Tests in this module **must be run via Bazel from that directory**.
- Use the command documented in `community/platform/build-scripts/bazel/README.md`:

```bash
cd community/platform/build-scripts/bazel
../../../../bazel.cmd test //:bazel-generator-integration-tests --test_output=all
```

- Do **not** use `./tests.cmd` for `org.jetbrains.intellij.build.bazel.BazelGeneratorIntegrationTests` or other tests in that module.
- If a request touches files under `community/platform/build-scripts/bazel`, prefer that module-local `../../../../bazel.cmd test` flow for verification.

## How tests.cmd Works

1. `tests.cmd` is a cross-platform script (works on Windows/Linux/macOS)
2. It takes `--module` and `--test`, maps them to JVM properties, and calls `./bazel.cmd run //build:local_idea_ultimate_run_tests_build_target`
3. The test runner uses JUnit to execute the specified test classes

**Troubleshooting test discovery:**
- Bazel incremental compilation works correctly — remote caches do NOT cause staleness, don't waste time on `./bazel.cmd clean`
- Test discovery issues are typically caused by:
  1. Wrong test pattern (use FQN or wildcard, not simple class name)
  2. Wrong module
  3. Test class not in the correct test module's classpath

For deeper troubleshooting, see [TESTING-internals.md](../testing-internals/SKILL.md).

## tests.cmd Parameters

```
Usage: tests.cmd --module <module> --test <pattern> [options]

Required:
  --module <module>    Name of the JPS module which contains the test classes
  --test <pattern>     Full test class name (FQN) or wild card pattern (e.g. com.intellij.*Test) or exact FQN#methodName

Options:
  --debug              Debug build scripts JVM process
  --help               Show this help message

Additional options are passed as JVM flags to org.jetbrains.intellij.build.TestingOptions
  Example: -Dintellij.build.test.debug.enabled=true -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005
```

### Additional JVM options

Extra `-D...` arguments are passed through as JVM flags to `org.jetbrains.intellij.build.TestingOptions`:

**`-Dintellij.build.test.attempt.count=<n>`**
- Retry failed tests N times
- Default: 1 (no retries)
- Use 3 for flaky tests

**`-Dintellij.build.test.jvm.memory.options=<options>`**
- Custom JVM memory options for the test process
- Example: `-Xmx8g` for 8GB heap

**`-Dpass.<property>=<value>`**
- Pass arbitrary system properties to the test JVM
- The `pass.` prefix is stripped, so `-Dpass.my.flag=true` becomes `-Dmy.flag=true` in the test JVM

**Debugging:** `-Dintellij.build.test.debug.enabled=true -Dintellij.build.test.debug.port=5005 -Dintellij.build.test.debug.suspend=true` — attach IDE debugger to port 5005.

## Troubleshooting

**Tests fail with OutOfMemoryError:**
- Increase heap size: `-Dintellij.build.test.jvm.memory.options=-Xmx8g`
- Check for memory leaks in test code

**Tests not found:**
- Verify `--test` uses FQN or wildcard, not simple class name: `--test com.example.MyTest`
- Check that `--module` is the module that actually contains the test class (look at the .iml file location)
- Before troubleshooting further, check whether the test lives in a separate Bazel module such as `community/platform/build-scripts/bazel`; those tests must be run with module-local `../../../../bazel.cmd test`, not `tests.cmd`
- If `tests.cmd` refuses the module and names a pattern from `bazel-migrated-test-modules.txt`, the tests are Bazel-only. Run them with `./community/tools/bt.cmd --module <module> --filter <FQN>`, as in [Separate Bazel Modules](#separate-bazel-modules)

**Tests pass locally but fail in CI:**
- Check test isolation - tests may depend on execution order
- Verify environment variables and system properties
- Use `-Dintellij.build.test.attempt.count=3` for flaky tests

**Bazel build fails before tests run:**
- Check module dependencies in `.iml` file
- Consult [module-dependencies.md](../module-dependencies/SKILL.md)

For deeper troubleshooting, see [TESTING-internals.md](../testing-internals/SKILL.md).

## Test Execution Internals

For detailed information about how `tests.cmd` works internally, including:
- Execution flow diagrams
- Key classes reference
- TestingOptions properties
- Bazel target configuration
- Test discovery flow

See [TESTING-internals.md](../testing-internals/SKILL.md)

## Writing Tests

For guidelines on **writing** tests (as opposed to running them), see the guidelines:
- [Writing Tests](../writing-tests/SKILL.md) - How to write tests, always consult before writing new tests

## Additional Resources

- [Running and Testing Documentation](../../../../docs/IntelliJ-Platform/2_Running-and-Testing)
- [Community README](../../../../community/README.md)
- [Main README](../../../../README.md)
- [Driver UI testing](../driver-ui-tests/SKILL.md)
