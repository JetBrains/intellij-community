---
name: testing
description: Comprehensive testing reference for running tests in IntelliJ codebase via tests.cmd. Use when running, debugging, or troubleshooting test execution.
---

# Testing Guide for IntelliJ IDEA

## Quick Start

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

### Windows PowerShell note

When running `tests.cmd` from PowerShell, pass JVM `-D...` arguments via stop-parsing mode to avoid argument mangling:

```powershell
./tests.cmd --% -Dintellij.build.test.patterns=com.example.MyTest
```

Without `--%`, PowerShell can alter `-D...` arguments before they reach `tests.cmd`, which may lead to errors like `Could not find or load main class ...`.

## Separate Bazel Modules

Some parts of the repository are standalone Bazel modules and must not use `tests.cmd` or `community/tests.cmd`.

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
2. It takes `--module` and `--test`, maps them to JVM properties, and calls `bazel run //build:local_idea_ultimate_run_tests_build_target`
3. The test runner uses JUnit to execute the specified test classes

**Troubleshooting test discovery:**
- Bazel incremental compilation works correctly — remote caches do NOT cause staleness, don't waste time on `bazel clean`
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
  Example: -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005
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
- Check that class name ends with `Test` (or use `-Dpass.idea.include.unconventionally.named.tests=true`)
- Before troubleshooting further, check whether the test lives in a separate Bazel module such as `community/platform/build-scripts/bazel`; those tests must be run with module-local `../../../../bazel.cmd test`, not `tests.cmd`

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

- [Running and Testing Documentation](../docs/IntelliJ-Platform/2_Running-and-Testing)
- [Community README](../community/README.md)
- [Main README](../README.md)
- [Driver UI testing](../driver-ui-tests/SKILL.md)
