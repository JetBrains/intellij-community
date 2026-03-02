---
name: testing-internals
description: Internals of tests.cmd execution flow, test discovery, and troubleshooting. Use when debugging test discovery issues or understanding the test runner.
---

# Test Execution Internals

This document explains `tests.cmd` internals and helps troubleshoot test execution issues.

## Overview

The test execution chain:
```
tests.cmd → Bazel → IdeaUltimateRunTestsBuildTarget → TestingTasksImpl → JUnit 5
```

Key components:
- **tests.cmd**: Shell script that invokes Bazel with test parameters
- **Bazel target**: `//build:idea_ultimate_run_tests_build_target`
- **TestingOptions**: Parses `-Dintellij.build.test.*` system properties
- **TestingTasksImpl**: Orchestrates classpath assembly and JVM setup
- **JUnit runners**: Execute tests in a forked JVM process

## Common Issues & Solutions

### Tests Not Found

**Symptoms:**
- "No tests found" message
- Test class exists but isn't executed

**Causes & Solutions:**

1. **Pattern mismatch** - Simple class names don't work (see Pattern Matching below):
   ```bash
   # WRONG - simple name won't match FQN
   -Dintellij.build.test.patterns=MyTest
   
   # CORRECT - use wildcard or FQN
   -Dintellij.build.test.patterns=*MyTest
   -Dintellij.build.test.patterns=com.example.MyTest
   ```

2. **Test not in classpath** - The test class must be in a module that's part of the test classpath. Check if the module is included in the build.

3. **Test class not recognized** - Ensure class name ends with `Test` or has JUnit annotations.

### OutOfMemoryError

**Solution:** Increase heap size:
```bash
./tests.cmd \
  -Dintellij.build.test.patterns=MyTest \
  -Dintellij.build.test.jvm.memory.options=-Xmx8g
```

### Bazel Build Fails

**Symptoms:**
- Build errors before tests run
- Missing dependencies

**Solutions:**

1. **Check module dependencies** - Ensure test module has required dependencies in `.iml` file.

2. **Verify BUILD.bazel is synced** - Run `./build/jpsModelToBazel.cmd` after changing `.iml` files.

**Note:** Bazel incremental builds are always correct. Do not use `bazel clean` - it won't help.

### Test Discovery Issues

**Symptoms:**
- Wrong tests executed
- Tests filtered unexpectedly

**Debug steps:**

1. Check test groups - If using groups, verify `testGroups.properties` configuration.

2. Check bucketing - For parallel execution, tests are distributed by hash:
   ```bash
   # See which bucket a test falls into
   -Didea.test.runners.count=4
   -Didea.test.runner.index=0  # Run only bucket 0
   ```

3. Check class filters - `TestCaseLoader` applies pattern matching before test execution.

### Debug Mode

Enable debug mode to attach a debugger:
```bash
./tests.cmd \
  -Dintellij.build.test.patterns=MyTest \
  -Dintellij.build.test.debug.enabled=true \
  -Dintellij.build.test.debug.port=5005 \
  -Dintellij.build.test.debug.suspend=true
```

Then attach debugger to port 5005.

## Quick Reference

| Property | Purpose |
|----------|---------|
| `intellij.build.test.patterns` | Test class patterns (semicolon-separated) |
| `intellij.build.test.groups` | Test groups to run |
| `intellij.build.test.attempt.count` | Retry count for flaky tests |
| `intellij.build.test.jvm.memory.options` | JVM memory settings |
| `intellij.build.test.debug.enabled` | Enable remote debugging |

---

## Detailed Reference

### Execution Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  1. COMMAND LINE                                                            │
│     ./tests.cmd -Dintellij.build.test.patterns=MyTest                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  2. SHELL SCRIPT                                                            │
│     tests.cmd → community/build/run_build_target.sh                         │
│     Converts args to --jvm_flag=<arg> format                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  3. BAZEL                                                                   │
│     bazel run //build:idea_ultimate_run_tests_build_target                  │
│     (defined in build/BUILD.bazel)                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  4. BUILD TARGET ENTRY POINT                                                │
│     IdeaUltimateRunTestsBuildTarget.main()                                  │
│     → UltimateProjectTestingTasks.runTests()                                │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  5. TEST OPTIONS PARSING                                                    │
│     UltimateProjectTestingOptions (extends TestingOptions)                  │
│     Reads all -Dintellij.build.test.* system properties                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  6. TEST EXECUTION ORCHESTRATION                                            │
│     TestingTasksImpl.runTests()                                             │
│     - Builds test classpath                                                 │
│     - Prepares JVM arguments and system properties                          │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  7. FORKED TEST PROCESS                                                     │
│     TestingTasksImpl.runJUnit5Engine()                                      │
│     Spawns new JVM with bootstrap classpath                                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┴───────────────┐
                    ▼                               ▼
┌───────────────────────────────────┐ ┌───────────────────────────────────────┐
│  8a. JUNIT 5 TESTS                │ │  8b. JUNIT 3/4 TESTS (Legacy)         │
│  JUnit5TeamCityRunnerFor-         │ │  JUnit5TeamCityRunnerFor-             │
│  TestsOnClasspath.main()          │ │  TestAllSuite.main()                  │
│  - Uses JUnit Platform Launcher   │ │  - Wraps legacy tests in JUnit 5     │
│  - ClassNameFilter                │ │  - BootstrapTests.suite()             │
│  - PostDiscoveryFilter            │ │  - TestAll.run()                      │
└───────────────────────────────────┘ └───────────────────────────────────────┘
                    │                               │
                    └───────────────┬───────────────┘
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  9. TEST DISCOVERY & FILTERING                                              │
│     TestCaseLoader                                                          │
│     - Loads test classes from classpath roots                               │
│     - Applies pattern/group filters                                         │
│     - Handles bucketing for parallel execution                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  10. TEST EXECUTION                                                         │
│      JUnit Platform executes tests                                          │
│      TCExecutionListener reports results to TeamCity                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Classes Reference

#### Entry Points & Orchestration

| Class | Purpose |
|-------|---------|
| `IdeaUltimateRunTestsBuildTarget` | Ultimate tests entry point, calls `UltimateProjectTestingTasks` |
| `CommunityRunTestsBuildTarget` | Community tests entry point, calls `TestingTasks` |
| `UltimateProjectTestingTasks` | Ultimate-specific test orchestration (YourKit, network restrictions) |
| `TestingTasks` | Interface for test execution |
| `TestingTasksImpl` | Core test execution logic |

#### Test Options

| Class | Purpose |
|-------|---------|
| `TestingOptions` | **Base class for all test options**. Parses `-Dintellij.build.test.*` properties |
| `UltimateProjectTestingOptions` | Ultimate-specific options (YourKit, skip community tests) |

#### JUnit 5 Test Runners

| Class | Purpose |
|-------|---------|
| `JUnit5TeamCityRunnerForTestsOnClasspath` | Runs JUnit 5 tests, uses `Launcher` API |
| `JUnit5TeamCityRunner` | Runs JUnit 3/4 tests using the JUnit Vintage test engine, or JUnit5 tests using the JUnit Jupiter test engine |
| `TCExecutionListener` | Reports test results to TeamCity via service messages |

#### Test Discovery & Loading

| Class | Purpose |
|-------|---------|
| `TestCaseLoader` | Discovers and filters test classes |
| `TestAll` | JUnit 3 test suite, collects all tests |
| `BootstrapTests` | Bootstrap suite for JUnit 3/4 tests |
| `TestClassesFilter` | Pattern/group-based test filtering |

#### Bucketing & Distribution

| Class | Purpose |
|-------|---------|
| `BucketingScheme` | Interface for test distribution |
| `HashingBucketingScheme` | Default: hash-based distribution |
| `TestsDurationBucketingScheme` | Duration-aware distribution |
| `NastradamusBucketingScheme` | AI-powered test distribution |

## Test Module Hierarchy

### Entry Points by Product

| Product | Entry Point | Default mainModule | Source |
|---------|-------------|-------------------|--------|
| IDEA Ultimate | `IdeaUltimateRunTestsBuildTarget` | `intellij.idea.ultimate.tests.main` | `build/src/` |
| Community | `CommunityRunTestsBuildTarget` | `intellij.idea.community.main.tests` | `community/build/src/` |
| RustRover | `RustRoverRunTestsBuildTarget` | `intellij.idea.ultimate.tests.main` | `rustrover/build/src/` |
| RubyMine | `RubyRunTestsBuildTarget` | `intellij.idea.ultimate.tests.main` | `ruby/build/src/` |
| CLion | `CLionRunTestsBuildTarget` | `intellij.idea.ultimate.tests.main` | `CIDR/clion-build/src/` |

**Note:** Product entry points (RustRover, RubyMine, CLion) inherit `intellij.idea.ultimate.tests.main` as the default, but to run product-specific tests, use the dedicated test module with `-Dintellij.build.test.main.module`. See [TESTING.md](../testing/SKILL.md#known-test-modules-by-product) for the correct module per product.

### CI-Defined Test Modules

From `intellij-teamcity-config/.teamcity/src/ijplatform/KnownModules.kt`:

| CI Constant | Module Name |
|-------------|-------------|
| `ULTIMATE_TESTS` | `intellij.idea.ultimate.tests.main` |
| `COMMUNITY_MAIN` | `intellij.idea.community.main.tests` |
| `GOLAND_TESTS` | `intellij.goland.tests` |
| `PYTHON_TESTS` | `intellij.python.tests` |
| `PHPSTORM_MAIN` | `intellij.phpstorm.main.tests` |
| `CLION_MAIN` | `intellij.clion.main.tests` |
| `RUSTROVER_MAIN` | `intellij.rustrover.main.tests` |
| `KOTLIN_K2_TESTS` | `kotlin.fir-all-tests` |
| `KOTLIN_ULTIMATE_ALL_TESTS` | `intellij.kotlin-ultimate.all-tests` |
| `DATABASE_TESTS` | `intellij.database.tests` |
| `DATABASE_SQL_TESTS` | `intellij.database.sql.tests` |

### Module Configuration

Default mainModule is set in:
- `UltimateProjectTestingOptions.kt:36` - Ultimate: `intellij.idea.ultimate.tests.main`
- `CommunityRunTestsBuildTarget.kt:28` - Community: `intellij.idea.community.main.tests`

### Ultimate Test Module Tree (Simplified)

```
intellij.idea.ultimate.tests.main
├── intellij.idea.ultimate.tests
├── intellij.idea.ultimate.tests.kotlin
├── intellij.platform.tests
├── intellij.java.tests
└── ... (hundreds of test modules)

Separate hierarchies (NOT in .main):
├── intellij.idea.ultimate.tests.kotlin.k2
│   └── intellij.devkit.kotlin.fir.tests
├── intellij.idea.ultimate.tests.devBuildTests
└── kotlin.fir-all-tests (K2/FIR tests)
```

## TestingOptions Properties

### TestingOptions Properties

Most options use the `intellij.build.test.*` prefix. Bucketing uses `idea.test.*` prefix.

```kotlin
// Test selection (mutually exclusive, in priority order)
testConfigurations  // -Dintellij.build.test.configurations=<config>
testPatterns        // -Dintellij.build.test.patterns=<pattern>
testGroups          // -Dintellij.build.test.groups=<group>

// Test execution
mainModule          // -Dintellij.build.test.main.module=<module>
bootstrapSuite      // -Dintellij.build.test.bootstrap.suite=<class>
attemptCount        // -Dintellij.build.test.attempt.count=<n>

// JVM configuration
jvmMemoryOptions    // -Dintellij.build.test.jvm.memory.options=<opts>
customRuntimePath   // -Dintellij.build.test.jre=<path>

// Debugging
isDebugEnabled      // -Dintellij.build.test.debug.enabled=<bool>
debugPort           // -Dintellij.build.test.debug.port=<port>
isSuspendDebugProcess // -Dintellij.build.test.debug.suspend=<bool>

// Bucketing (parallel execution) - NOTE: uses idea.test.* prefix
bucketsCount        // -Didea.test.runners.count=<n>
bucketIndex         // -Didea.test.runner.index=<n>

// Coverage
enableCoverage      // -Dintellij.build.test.coverage.enabled=<bool>
coveredClassesPatterns // -Dintellij.build.test.coverage.include.class.patterns=<patterns>
```

### Bazel Target Configuration

The test target is defined in `build/BUILD.bazel`:

```python
java_binary(
  name = "idea_ultimate_run_tests_build_target",
  runtime_deps = [":build"],
  main_class = "IdeaUltimateRunTestsBuildTarget",
  data = ALL_ULTIMATE_TARGETS,
  jvm_flags = [
    "-Dintellij.build.console.exporter.to.temp.file=true",
    "-Dintellij.build.console.messages.verbose=false",
    "-Dintellij.build.clean.output.root=false",      # Reuse compiled classes
    "-Dintellij.build.use.compiled.classes=true",    # Skip recompilation
  ],
  add_opens = INTELLIJ_ADD_OPENS,
)
```

### Test Process JVM Configuration

`TestingTasksImpl.prepareEnvForTestRun()` configures the forked test JVM:

```kotlin
// Key system properties set for test process:
"idea.home.path"     → projectHome
"idea.config.path"   → tempDir/config
"idea.system.path"   → tempDir/system
"java.io.tmpdir"     → tempDir
"classpath.file"     → path to file with test classpath
"bootstrap.testcases" → "com.intellij.AllTests" (or custom suite)

// JVM options:
"-XX:+HeapDumpOnOutOfMemoryError"
"-XX:HeapDumpPath=<snapshotsDir>/intellij-tests-oom-<timestamp>.hprof"
"-Xms750m -Xmx1024m"  // or custom from jvmMemoryOptions
// Plus --add-opens for module access
```

### Passing JVM Args to Test Process

There are two mechanisms for passing JVM arguments to the test JVM process:

#### 1. Memory Options (`intellij.build.test.jvm.memory.options`)

For JVM memory settings like heap size, use the dedicated property:

```bash
./tests.cmd -Dintellij.build.test.jvm.memory.options="-Xmx4g -Xms2g"
```

Multiple options are space-separated within quotes. These options are added to the beginning of the JVM arguments via `VmOptionsGenerator.generate()`.

**Implementation** (see `TestingTasksImpl.kt`, `runJUnit5Engine` method):
```kotlin
val customMemoryOptions = options.jvmMemoryOptions?.trim()?.split(Regex("\\s+"))?.takeIf { it.isNotEmpty() }
jvmArgs.addAll(
  index = 0,
  elements = VmOptionsGenerator.generate(
    customVmMemoryOptions = if (customMemoryOptions == null) mapOf("-Xms" to "750m", "-Xmx" to "1024m") else emptyMap(),
    additionalVmOptions = customMemoryOptions ?: emptyList(),
    // ... other parameters omitted
  ),
)
```

#### 2. Pass-through System Properties (`pass.*` prefix)

To pass arbitrary system properties to the test JVM, use the `pass.` prefix. The prefix is stripped before passing to the test process:

```bash
./tests.cmd -Dpass.my.custom.property=value -Dpass.some.flag=true
```

Results in test JVM receiving:
- `-Dmy.custom.property=value`
- `-Dsome.flag=true`

**Implementation** (see `TestingTasksImpl.kt`, `prepareEnvForTestRun` method):
```kotlin
for ((key, value) in System.getProperties()) {
  key as String
  if (key.startsWith("pass.")) {
    systemProperties.put(key.substring("pass.".length), value as String)
  }
}
```

This is a TeamCity convention for passing properties to nested processes.

#### Combined Example

```bash
./tests.cmd \
  -Dintellij.build.test.patterns=MyTest \
  -Dintellij.build.test.jvm.memory.options="-Xmx4g" \
  -Dpass.my.test.flag=enabled \
  -Dpass.debug.level=verbose
```

**Important**: Properties without `pass.` prefix are consumed by the build scripts, NOT passed to the test JVM.

### Test Discovery Flow

### Step-by-Step Pattern Matching

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  PATTERN INPUT                                                              │
│  -Dintellij.build.test.patterns=*MyTest                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PATTERN COMPILATION (TestClassesFilter.compilePattern)                     │
│  filter.replace("$","\\$").replace(".","\\.");replace("*",".*")             │
│  "*MyTest" → ".*MyTest"                                                     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  CLASS DISCOVERY (ClassFinder)                                              │
│  Scans JARs for *Test.class files, extracts FQN:                            │
│  org/example/MyTest.class → "org.example.MyTest"                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  PATTERN MATCHING (PatternListTestClassFilter.matches)                      │
│  pattern.matcher(className).matches()  ← FULL STRING MATCH                  │
│  ".*MyTest".matches("org.example.MyTest") → true                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Pattern Transformation Table

| Input Pattern | Compiled Regex | Matches `org.example.MyTest`? |
|---------------|----------------|-------------------------------|
| `MyTest` | `MyTest` | ❌ NO (not full match) |
| `*MyTest` | `.*MyTest` | ✅ YES |
| `org.example.MyTest` | `org\.example\.MyTest` | ✅ YES |
| `org.example.*` | `org\.example\..*` | ✅ YES |
| `*` | `.*` | ✅ YES (matches all) |

### Key Implementation Details

1. **Pattern Compilation** (`TestClassesFilter.compilePattern()`):
   ```java
   filter = filter.replace("$", "\\$").replace(".", "\\.").replace("*", ".*");
   return Pattern.compile(filter);
   ```

2. **Pattern Matching** (`PatternListTestClassFilter.matches()`):
   ```java
   return ContainerUtil.exists(patterns, pattern -> pattern.matcher(className).matches());
   ```
   - Uses `matches()` NOT `find()` - requires ENTIRE string to match
   - className is ALWAYS the fully qualified name (FQN)

3. **ClassNameFilter** (JUnit 5) - Fast pre-filter on class names
   - Calls `TestCaseLoader.isClassNameIncluded(className)`
   - Applied to EVERY class in classpath (must be fast)

4. **PostDiscoveryFilter** (JUnit 5) - Post-discovery filter
   - Calls `TestCaseLoader.isClassIncluded(className)`  
   - Checks bucketing (which runner should execute this test)

5. **TestCaseLoader.fillTestCases()** (JUnit 3/4) - Scans classpath roots
   - Uses `ClassFinder` to find all `*Test.class` files
   - Calls `isPotentiallyTestCase()` which calls filter's `matches()`
   - Applies group-based filtering from `testGroups.properties`

### Why Simple Class Names NEVER Work

**Root Cause**: `Pattern.matches()` requires the ENTIRE string to match.

```java
// Pattern: "MyTest" → Regex: "MyTest"
// className: "org.example.MyTest"
"MyTest".matches("org.example.MyTest")  // Returns FALSE

// Pattern: "*MyTest" → Regex: ".*MyTest"  
".*MyTest".matches("org.example.MyTest")  // Returns TRUE
```

This applies to ALL modules (default and non-default). Always use:
- FQN: `org.example.MyTest`
- Or wildcard: `*MyTest`

---

## Related Documentation

- [TESTING.md](../testing/SKILL.md) - How to run tests via `tests.cmd` (quick start, parameters, examples)
- [Writing Tests](../writing-tests/SKILL.md) - How to write tests (framework, `@TestApplication`, fixtures, EDT)
