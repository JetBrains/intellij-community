---
name: testing
description: Comprehensive testing reference for running tests in IntelliJ codebase via tests.cmd. Use when running, debugging, or troubleshooting test execution.
---

# Testing Guide for IntelliJ IDEA

Comprehensive testing reference for running tests in the IntelliJ IDEA codebase.

## Quick Start

Run a single test class (use FQN or wildcard - simple names don't work):

```bash
# Option 1: Fully qualified name (recommended)
./tests.cmd -Dintellij.build.test.patterns=com.example.MyTest

# Option 2: Wildcard prefix
./tests.cmd -Dintellij.build.test.patterns=*MyTest
```

## Pattern Matching (CRITICAL)

**Simple class names like `MyTest` do NOT work.** You must use FQN or wildcard.

### How Pattern Matching Works

1. Your pattern is transformed: `*` → `.*`, `.` → `\.`
2. Test classes are discovered as FQN: `org.example.MyTest`
3. Pattern is matched using `Pattern.matches()` which requires **FULL STRING MATCH**

### Pattern Examples

| Pattern | Regex | Matches `org.example.MyTest`? |
|---------|-------|-------------------------------|
| `MyTest` | `MyTest` | ❌ NO |
| `*MyTest` | `.*MyTest` | ✅ YES |
| `org.example.MyTest` | `org\.example\.MyTest` | ✅ YES |
| `org.example.*` | `org\.example\..*` | ✅ YES |

### Valid Pattern Formats

```bash
# ✅ FQN (most reliable)
-Dintellij.build.test.patterns=org.jetbrains.idea.devkit.inspections.internal.JavaIoFileUsageInspectionTest

# ✅ Wildcard prefix (convenient)
-Dintellij.build.test.patterns=*JavaIoFileUsageInspectionTest

# ✅ Package wildcard
-Dintellij.build.test.patterns=org.jetbrains.idea.devkit.inspections.*Test

# ✅ Multiple patterns (semicolon-separated)
-Dintellij.build.test.patterns=*MyTest;*OtherTest

# ❌ WRONG - Simple name (will NOT work)
-Dintellij.build.test.patterns=JavaIoFileUsageInspectionTest
```

## How tests.cmd Works

1. `tests.cmd` is a cross-platform script (works on Windows/Linux/macOS)
2. It calls `bazel run //build:idea_ultimate_run_tests_build_target`
3. Arguments are passed as JVM options to the test runner
4. The test runner uses JUnit to execute the specified test classes

**Troubleshooting test discovery:**
- Bazel incremental compilation works correctly - remote caches do NOT cause staleness
- If tests are not found, it's NOT a compilation staleness issue - don't waste time on `bazel clean`
- Test discovery issues are typically caused by, in order:
  1. Wrong test pattern (use FQN or wildcard, not simple class name)
  2. Wrong main module for running tests
  3. Test class not in the correct test module's classpath

For deeper troubleshooting, see [TESTING-internals.md](../testing-internals/SKILL.md).

## Test Script Parameters

The [tests.cmd](../tests.cmd) script supports the following parameters:

### Test Configuration

**`-Dintellij.build.test.attempt.count=<n>`**
- Retry failed tests N times
- Default: 1 (no retries)
- Use 3 for flaky tests

**`-Dintellij.build.test.main.module=<module>`**
- Specify which module's classpath to use for testing

**`-Dintellij.build.test.jvm.memory.options=<options>`**
- Custom JVM memory options for the test process
- Example: `-Xmx8g` for 8GB heap

## Test Modules and Classpath

When running tests, the `mainModule` parameter determines which module's classpath is used for test discovery. Tests must be in the dependency tree of the specified module to be found.

### Default Main Modules

| Product | tests.cmd Location | Default mainModule |
|---------|-------------------|-------------------|
| IDEA Ultimate | `./tests.cmd` | `intellij.idea.ultimate.tests.main` |
| Community | `./community/tests.cmd` | `intellij.idea.community.main.tests` |

### Known Test Modules by Product

For complete list, see: `intellij-teamcity-config/.teamcity/src/ijplatform/KnownModules.kt`

| Product | Test Module |
|---------|-------------|
| IDEA Ultimate | `intellij.idea.ultimate.tests.main` |
| IDEA Community | `intellij.idea.community.main.tests` |
| GoLand | `intellij.goland.tests` |
| PyCharm | `intellij.python.tests` |
| RubyMine | `intellij.rubymine.aggregator` |
| CLion | `intellij.clion.main.tests` |
| PhpStorm | `intellij.phpstorm.main.tests` |
| RustRover | `intellij.rustrover.main.tests` |
| Kotlin K2/FIR | `kotlin.fir-all-tests` |

### Finding Tests in Other Modules

If your test is not in the default module's dependency tree, specify the module explicitly:

```bash
# K2/FIR tests (not in default classpath)
./tests.cmd -Dintellij.build.test.patterns=org.jetbrains.idea.devkit.k2.inspections.K2JavaIoFileUsageInspectionTest \
            -Dintellij.build.test.main.module=intellij.devkit.kotlin.fir.tests

# GoLand tests
./tests.cmd -Dintellij.build.test.patterns=*GoCommentReferencesTest \
            -Dintellij.build.test.main.module=intellij.goland.tests
```

### Troubleshooting: Test Not Found

If tests.cmd reports "No tests found":
1. Check the module containing your test (look at the .iml file location)
2. Verify if that module is a dependency of the default mainModule
3. If not, specify `-Dintellij.build.test.main.module=<your-test-module>`

### Test Selection (Choose ONE)

**`-Dintellij.build.test.patterns=<pattern>`**
- Semicolon-separated patterns for test class names
- Wildcard '*' is supported
- When specified, test groups are ignored
- Example: `-Dintellij.build.test.patterns=com.goide.comments.GoCommentReferencesTest`
- Example with multiple: `-Dintellij.build.test.patterns=com.goide.*.GoCommentTest;com.goide.*.GoReferenceTest`

**`-Dintellij.build.test.groups=<group>`**
- Semicolon-separated names of test groups to execute
- Test groups are defined in `testGroups.properties` files
- Special groups:
  - `ALL_EXCLUDE_DEFINED_GROUP` - Tests not included in any group (default)
  - `ALL` - All tests

**`-Dintellij.build.test.configurations=<config>`**
- Semicolon-separated names of JUnit run configurations to execute
- When specified, test groups, patterns, and main module are ignored
- Example: `-Dintellij.build.test.configurations=Go Tests`

## Common Test Scenarios

### Quick Single Test (Recommended for Development)

Fast execution for a single test class:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.goide.comments.GoCommentReferencesTest
```

### Run Tests with Retries (for flaky tests)

Automatically retry failed tests:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.FlakyTest \
  -Dintellij.build.test.attempt.count=3
```

### Run Multiple Test Classes

Use patterns with wildcards:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.goide.comments.*Test
```

### Run Tests with Custom Memory

For memory-intensive tests:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.MemoryIntensiveTest \
  -Dintellij.build.test.jvm.memory.options=-Xmx8g
```

### Pass System Properties to Test JVM

To pass arbitrary system properties to the test process, use the `pass.` prefix:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.MyTest \
  -Dpass.my.custom.property=myValue
```

The `pass.` prefix is stripped, so the test JVM receives `-Dmy.custom.property=myValue`.

Combined example with memory and properties:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.MyTest \
  -Dintellij.build.test.jvm.memory.options="-Xmx4g" \
  -Dpass.idea.is.unit.test=true \
  -Dpass.my.debug.flag=enabled
```

### Run Test Group

Execute all tests in a specific group:

```bash
./tests.cmd \
  -Dintellij.build.test.groups=CRITICAL_TESTS
```

### Run JUnit Configuration

Execute a named JUnit run configuration:

```bash
./tests.cmd \
  "-Dintellij.build.test.configurations=Go Tests"
```

## Best Practices

1. **Run specific tests** - Use patterns to run only relevant tests during development
2. **Retry flaky tests** - Use `-Dintellij.build.test.attempt.count=3` for known flaky tests
3. **Monitor memory usage** - Increase heap size if tests are failing with OOM errors
4. **Use debug mode** - Attach a debugger when investigating test failures (see below)

### Debugging Tests

To attach a debugger to the test JVM:

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.MyTest \
  -Dintellij.build.test.debug.enabled=true \
  -Dintellij.build.test.debug.port=5005 \
  -Dintellij.build.test.debug.suspend=true
```

Then attach your IDE debugger to port 5005. The test process will wait for the debugger before starting.

## Running Performance Tests

Performance tests have class or method names containing "Performance". By default, they are **excluded** from regular test runs.

### Run Performance Tests Only

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=*PerformanceTest \
  -Dpass.idea.performance.tests=true
```

**Note:** When `idea.performance.tests=true`, debugging is automatically disabled.

### Include Performance Tests with Regular Tests

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.example.* \
  -Dpass.idea.include.performance.tests=true
```

### Performance Test Detection

A test is considered a "performance test" if its class name or method name contains the word "performance" (case-insensitive). See `TestFrameworkUtil.isPerformanceTest()`.

Similarly, "stress tests" contain "stress" or "slow" in their names.

## Running Tests with Unconventional Names

By default, only test classes ending with `Test` are discovered. To include tests with unconventional names (like `*Tests`, `*TestCase`, `*Suite`, or names containing `Test` elsewhere):

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=*MySuite \
  -Dpass.idea.include.unconventionally.named.tests=true
```

### Test Name Detection Rules

Without `idea.include.unconventionally.named.tests`:
- Only classes ending with `Test` are discovered

With `idea.include.unconventionally.named.tests=true`:
- Classes containing `Test` or `Suite` anywhere in their name
- The name must have a word boundary (detected via `NameUtilCore.nameToWordList`)
- Examples that match: `MyTestCase`, `AllTests`, `TestSuite`, `MySuiteRunner`
- Examples that don't match: `Testing`, `Contest` (no word boundary)

## Troubleshooting

**Tests fail with OutOfMemoryError:**
- Increase heap size: `-Dintellij.build.test.jvm.memory.options=-Xmx8g`
- Check for memory leaks in test code

**Tests not found:**
- Verify pattern uses class name, not file path: `-Dintellij.build.test.patterns=com.example.MyTest`
- Check that class name ends with `Test` (or use `-Dpass.idea.include.unconventionally.named.tests=true`)
- Ensure test module is part of the build classpath
- For performance tests, add `-Dpass.idea.performance.tests=true` or `-Dpass.idea.include.performance.tests=true`

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
