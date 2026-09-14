---
name: module-dependencies
description: Add or modify IntelliJ module dependencies in `.iml` files.
---

# Module Dependencies Management

This document describes how to manage module dependencies when working with IntelliJ IDEA codebase.

## Documentation

- [Modularization Overview](../../../../docs/IntelliJ-Platform/4_man/Modularization/) - Module organization principles:
  - [Granularity of Modules](../../../../docs/IntelliJ-Platform/4_man/Modularization/0_Granularity-of-modules.md)
  - [Modules in Project Sources](../../../../docs/IntelliJ-Platform/4_man/Modularization/1_Modules-in-project-sources.md)
  - [Modules at Runtime](../../../../docs/IntelliJ-Platform/4_man/Modularization/2_Modules-at-runtime.md)
  - [Modules in Build Scripts](../../../../docs/IntelliJ-Platform/4_man/Modularization/3_Modules-in-build-scripts.md)
- [Project Structure](../../../../docs/IntelliJ-Platform/4_man/Project-Structure/) - Naming conventions, Kotlin usage, dependencies

## Build System Overview

The repository uses a hybrid build system:
- **JPS (*.iml files)**: Source of truth for module dependencies
- **Bazel (BUILD files)**: Auto-generated from *.iml files
- **Generator**: run `build/jpsModelToBazel.cmd` after changing .iml files

## JPS Module Registration Helper

When creating or editing a JPS module `.iml`, use the helper instead of hand-editing project module lists:

```bash
bun build/jps-module.mjs register <path-to-iml> --fix-iml-eof
```

The helper:
- registers the module in `.idea/modules.xml`
- also registers `community/...` modules in `community/.idea/modules.xml`
- inserts entries in the canonical order: `.iml` basename without the `.iml` suffix, matching `org.jetbrains.intellij.build.ModulesXml`
- removes trailing line breaks from the listed `.iml` files when `--fix-iml-eof` is passed

Use `bun build/jps-module.mjs check <path-to-iml> --fix-iml-eof` to verify without writing.

## Running the IML-to-Bazel Generator

To manually run the converter that generates Bazel BUILD files from .iml files:

```bash
./build/jpsModelToBazel.cmd
```

This is useful when:
- The automatic converter didn't run (e.g., .iml files were modified outside the IDE)
- You need to regenerate BUILD files after git operations
- Troubleshooting build system synchronization issues

## Library Version Changes

After you change the version of a repository library in an `*.iml` or in `.idea/libraries/*.xml`, run the Fleet generator in the monorepo checkout:

```bash
./fleet/build/generateProjectModel.cmd dump
```

The Fleet generator copies the JPS library versions into `fleet/build/gradle/jps.versions.toml`, `fleet/build/jps-library-mappings.tsv` and both `fleet/kmp.MODULE.bazel` files. Its `check` mode fails on drift.

When the dump changes a `kmp.MODULE.bazel`, update both Bazel lockfiles:

```bash
./bazel.cmd mod deps --lockfile_mode=update
(cd community && ./bazel.cmd mod deps --lockfile_mode=update)
```

The `kmp` module extension records its artifact list in the `MODULE.bazel.lock` of each module, and CI runs Bazel with `--lockfile_mode=error`, so a stale lockfile fails the build. `bun community/build/libraries-dashboard/libraries-dashboard.mjs bump` prints these commands after a bump.

Some files outside the JPS model copy a library version, and the project structure tests check each copy:

- `community/platform/jps-bootstrap/pom.xml` copies every library that jps-bootstrap uses (`JpsBoostrapStructureTest`).
- `JetBrainsAnnotationsExternalLibraryResolver.VERSION` copies `org.jetbrains:annotations` (`IdeaUltimateProjectStructureTest`).

The bump command rewrites these copies and reports a copy that still differs. After a manual version change, edit them by hand. Then run the tests that gate the Smoke Tests build:

```bash
./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'
```

These tests also check the Kotlin, Compose and LanguageTool version alignment and the unused project libraries.

## BUILD.bazel Auto-Generated Sections

BUILD.bazel files have auto-generated sections marked with comments:

```starlark
### auto-generated section `build module.name` start
... generated content ...
### auto-generated section `build module.name` end

### auto-generated section `test module.name` start
... generated content ...
### auto-generated section `test module.name` end
```

**Key rules:**
- Content **inside** auto-generated sections is overwritten by the generator
- Content **outside** auto-generated sections is preserved during regeneration

### Skip Generation Marker

To prevent auto-generation of a section (so you can provide custom content):

```starlark
### skip generation section `test module.name`
```

**Example - Custom test target with preserved content:**

```starlark
load("@community//build:tests-options.bzl", "jps_test")

# Custom test target (before auto-generated sections)
jps_test(
  name = "my-tests_test",
  runtime_deps = [
    ":my-tests_test_lib",
    "//:main_test_lib",
  ],
)

### skip generation section `test my.module.name`

### auto-generated section `build my.module.name` start
... (let generator handle the build section)
```

## Content Modules and Wrapper Plugins

Product layouts can include content modules directly. If production runtime already gets a dependency from a content module in the product layout, that content-module dependency can be enough and does not automatically require adding a wrapper plugin dependency to a production `.iml` or plugin descriptor.

Test plugin resolution is different because tests often do not run with the full production product layout or flat classpath. If a test module loads a plugin whose dependencies include content modules owned by a wrapper plugin, add the wrapper plugin as a test/runtime dependency in the test module `.iml` instead of broadening the production module dependency. For example, a test that needs Problems View content modules may need `intellij.platform.problemView.plugin` as a runtime dependency even when the production module only depends on Problems View content modules.

## Test Access to `internal` Declarations

A Kotlin `internal` declaration is visible to a test only when the test module is a friend of the
production module.

**The same module.** A test source root of a module sees the `internal` declarations of the
production sources. Nothing to configure. The generator adds the production target to the
`associates` attribute of the `_test_lib` target.

**A separate test module.** Add the `TestModuleProperties` component to the test module `.iml`:

```xml
  <component name="TestModuleProperties" production-module="intellij.platform.configurationStore.impl" />
```

Then run `./build/jpsModelToBazel.cmd`. The generator moves the production module from `deps` to
`associates`, and the Kotlin compiler gets it as a friend path. For a complete example, see
`community/notebooks/visualization/intellij.notebooks.visualization.tests.iml`.

The component holds one module. A test module cannot be a friend of two production modules, and the
friendship is not transitive.

**Java.** Java has no `internal` modifier. Put the test class in the same package as a
package-private declaration. For a member that carries `@ApiStatus.Internal`, add
`@VisibleForTesting` or `@TestOnly`, because the compiler makes no check here.

## Important Notes

When you need to fix missing dependencies:
1. Edit the `.iml` with the repo-approved edit tool.
2. Run `bun build/jps-module.mjs register <path-to-iml> --fix-iml-eof` for every changed module file.
3. Run `./build/jpsModelToBazel.cmd` so generated `BUILD.bazel` files match the `.iml` source of truth.
4. Verify compilation or tests for the affected module.
