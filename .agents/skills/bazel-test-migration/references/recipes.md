# Bazel Migration Recipes

Read only the section needed for the current failure.
The examples use Community labels from an Ultimate checkout.

## Test Data and Writable Files

Declare the files and add them to `data`. This example shows the relevant parts of the Ant target:

```starlark
load("@community//build:tests-options.bzl", "jps_test")

filegroup(
    name = "testData",
    srcs = glob(["testData/**"]),
)

jps_test(
    name = "jps-tests_test",
    data = [":testData"],
    runtime_deps = [":jps-tests_test_lib"],
)
### skip generation section `test intellij.ant.jps.tests`
```

Merge this wiring with the target's existing attributes. Declare shared fixture roots and generated files separately when the tests need them.

Use the same label in the test helper. For example, `JpsAntSerializationTest` resolves its project path in its companion object:

```kotlin
val PROJECT_PATH = if (BazelTestUtil.isUnderBazelTest) {
  val label = BazelLabel.fromString("@community//plugins/ant/jps-plugin:testData")
  BazelTestUtil.getFileFromBazelRuntime(label).resolve("ant-project").absolutePathString()
}
else {
  Path(PathManagerEx.getCommunityHomePath()).resolve("plugins/ant/jps-plugin/testData/ant-project").absolutePathString()
}
```

Use the existing imports and path conventions of the test.
For Java fixtures, prefer `JavaTestUtil.getJavaTestDataPath()` and append the test's subdirectory.
Override the absolute `getTestDataPath()` when the base implementation assumes a path relative to the repository.
Do not make `getRelativeJavaTestDataPath()` return an absolute path under a different contract.

`BazelRunfiles.getFileByLabel()` resolves the label's runtime path. It does not enumerate a filegroup's members.
A group named `testData` that contains `java/**` does not create a `testData` directory.
Choose the resolver path from the actual files and directory layout.
Use the returned path unchanged, whether it comes from a runfiles tree or a manifest.

### VFS Access

A VFS access failure does not mean that the test data path is wrong.
Check `CodeInsightTestFixtureImpl` and `VfsRootAccess` before adding another path conversion.
Register the required root through the fixture's existing access mechanism, with the appropriate disposable.

An indexer can follow leaf symlinks to a canonical root outside the runfiles tree.
For that case, inspect `BazelTestUtil.allowVfsAccessToCanonicalRunfilesRoot()`.
This helper grants access; it does not replace the test's selected input path.
Keep canonical VFS access handling separate from test data resolution.

### Writable Copies

- Use the fixture's temporary directory, or a unique child of `BazelTestUtil.bazelTestTmpDirPath` under Bazel.
- Check `isUnderBazelTest` before accessing Bazel-only temporary paths. Preserve the normal fixture behavior outside Bazel.
- `TEST_TMPDIR` belongs to the test process, not to one method. Avoid fixed directory names that collide between tests.
- Copy input projects or unpack archives before the test modifies them. Treat runfiles as read-only even when the local filesystem permits writes.
- Put diagnostics that must survive the run in `BazelTestUtil.bazelUndeclaredTestOutputsPath`.

## External Inputs

First determine what the test needs from an archive.
If it tests only library roots, paths, or serialization, use a small valid local archive or generate one in a temporary directory.
Keep real artifacts when their classes or contents are part of the test.

For required downloads:

1. Declare the artifact with a pinned checksum through the existing `test_deps_repository` and `download_file` mechanism.
2. Register the repository in the appropriate Bazel module and add the artifact target to the test's `data`.
3. Resolve the artifact by label. Use `BazelTestDependencyHttpFileDownloader` when the same test must also download the input outside Bazel.
4. Unpack into a writable temporary directory. Keep the archive unchanged.

The downloader reads runfiles under Bazel. Outside Bazel, it reads the declaration and verifies the downloaded checksum.
Do not read checkout-based declarations eagerly during a Bazel run.
For an Ultimate example, inspect `language_server_test_api_dependencies.bzl` and `LanguageServerTestDependencyHttpFileDownloader`.

## Nested Builds

Tests that invoke JPS can need the generated module-to-target mapping in addition to test data.
Use `current_bazel_targets_json()` instead of hardcoding the Ultimate mapping in a Community target:

```starlark
load("@community//build:bazel_targets_json.bzl", "current_bazel_targets_json")
load("@community//build:tests-options.bzl", "jps_test")

BAZEL_TARGETS_JSON = current_bazel_targets_json()

jps_test(
    name = "jps-tests_test",
    data = [
        ":testData",
        BAZEL_TARGETS_JSON,
    ],
    jvm_flags = [
        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % BAZEL_TARGETS_JSON,
    ],
    runtime_deps = [":jps-tests_test_lib"],
)
```

Merge these attributes into the existing target and retain its skip-generation marker.
The Ant JPS target provides a complete example.

Propagate required runfiles and repository mapping information to child processes through the existing launch helpers.
Use the shared mapping to find module outputs. Do not guess jar locations from file contents or copy individual plugin jars into runfiles.
Distinguish the test JVM, a mock project SDK, and an executable compiler. A mock SDK is not necessarily a usable `javac` installation.

## Failure Diagnosis

| Failure | First check |
| --- | --- |
| Missing file or repository-root assertion | Check `data`, the resolver label, and any remaining checkout-based path construction. |
| VFS rejects an existing file | Check allowed roots and canonical traversal separately from path resolution. |
| Missing extension, action, inspection, or file type | Find the registering descriptor. Add the owning plugin as a runtime dependency when compilation does not need it. |
| Plugin action group is missing | Check the group owner's descriptor. Distinguish a missing test dependency from an invalid production dependency. |
| Child JPS process cannot find modules or jars | Check the target mapping, its backing runfile, and child process propagation. |
| Test passes alone but fails in the module | Check language levels, formatting settings, SDKs, project descriptors, and cleanup. |
| Failure occurs only on another platform | Compare path spelling, case sensitivity, manifests, locale, and declared environment. |
| Timeout | Distinguish the Bazel target timeout from a test method timeout before changing either. |

For slow CI runs, inspect the first expensive operation and the thread dumps.
Check indexing, service initialization, coroutine waits, and filesystem traversal.
Move one-time setup only when its lifecycle permits it. Wait for the required condition rather than increasing a timeout without evidence.
A target timeout can interrupt the currently active test. That test is not necessarily the cause of the delay.

For classpath or runfiles diagnostics, add one of these flags to the focused command:

```text
--test_env=JB_TEST_PRINT_SORTED_CLASSPATH=true
--test_env=JB_TEST_PRINT_TEST_SRCDIR_CONTENT=true
```

The runner also supports `JB_TEST_PRINT_ENV` and `JB_TEST_PRINT_SYSTEM_PROPERTIES`. Their output can contain secrets; inspect and redact it before sharing.

To reproduce interactions between classes, use the runner's semicolon-separated selector:

```bash
./bazel.cmd test --cache_test_results=no \
  --test_env='JB_TEST_FILTER=com.example.FirstTest;com.example.SecondTest' \
  @community//path/to/package:target_test
```

Remove diagnostic selectors before the final full-target run.
Use the [testing internals skill](../../testing-internals/SKILL.md) when the failure is in discovery or the runner itself.

## Discovery and Sandbox Checks

Check the generated `*_test_lib`, the target's runtime dependencies, and `JUnit5BazelRunner` discovery roots.
The runner does not scan every transitive test dependency.
A method selector can bypass normal root selection, so a focused pass does not prove complete discovery.

Tests mixed with framework code can require a dedicated module and Bazel package.
The Java framework tests use `@community//java/testFramework/tests:tests_test`.
Separate placement is appropriate when it clarifies ownership and discovery, not merely because a class currently fails.
Prefer that structure over a permanent list of individual test classes in the target filter.

Inspect `jps_test` in `tests-options.bzl` before changing sandbox options.
Its `sandbox = True` mode sets up an isolated test home and requests network blocking.
Its default is `False`, which permits checkout-based home discovery and adds `no-sandbox`.
Actual filesystem and network isolation also depends on the platform and execution strategy.

Declare inputs and writable paths before enabling the sandbox.
Do not disable an existing sandbox to mask undeclared inputs.
Keep a necessary platform workaround scoped to the affected target; do not add it to all test JVMs without evidence.

## CI Coverage Comparison

Use this procedure when checking a rollout or investigating missing tests:

1. Open the actual test builds. For a composite build, follow its dependencies to all operating systems, buckets, and shards.
2. Prefer the same revision and comparable environment. Record differences that prevent an exact comparison.
3. Fetch every result page and all statuses, including failed, ignored, and muted tests. Do not compare only successful occurrences.
4. Compare identities and invocation counts, not display names or headline totals. Normalize runner naming differences without removing parameters, repetitions, or overload signatures.
5. Distinguish retries from intended repeated invocations. When names collide, inspect JUnit XML or logs instead of collapsing records into a set.
6. Account for synthetic runner checks separately. Confirm that their purpose remains covered before treating them as reporting-only differences.
7. Compare the combined shard coverage. An intentionally idle shard can report success with no tests.
8. Report genuinely missing and extra tests, plus unresolved naming ambiguities. Recheck target selection and discovery before changing tests.

A green local target does not prove that CI or Safe-Push selects it.
Verify both configurations before removing their JPS coverage.
The migrated-module list controls runner routing; it is not evidence that a replacement CI target ran.
