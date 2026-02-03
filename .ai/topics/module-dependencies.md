# Module Dependencies Management

This document describes how to manage module dependencies when working with IntelliJ IDEA codebase.

## Documentation

- [Modularization Overview](../docs/IntelliJ-Platform/4_man/Modularization/) - Module organization principles:
  - [Granularity of Modules](../docs/IntelliJ-Platform/4_man/Modularization/0_Granularity-of-modules.md)
  - [Modules in Project Sources](../docs/IntelliJ-Platform/4_man/Modularization/1_Modules-in-project-sources.md)
  - [Modules at Runtime](../docs/IntelliJ-Platform/4_man/Modularization/2_Modules-at-runtime.md)
  - [Modules in Build Scripts](../docs/IntelliJ-Platform/4_man/Modularization/3_Modules-in-build-scripts.md)
- [Project Structure](../docs/IntelliJ-Platform/4_man/Project-Structure/) - Naming conventions, Kotlin usage, dependencies

## Build System Overview

The repository uses a hybrid build system:
- **JPS (*.iml files)**: Source of truth for module dependencies
- **Bazel (BUILD files)**: Auto-generated from *.iml files
- **Generator**: run `build/jpsModelToBazel.cmd` after changing .iml files

## Running the IML-to-Bazel Generator

To manually run the converter that generates Bazel BUILD files from .iml files:

```bash
./build/jpsModelToBazel.cmd
```

This is useful when:
- The automatic converter didn't run (e.g., .iml files were modified outside the IDE)
- You need to regenerate BUILD files after git operations
- Troubleshooting build system synchronization issues

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

## Important Notes

⚠️ **DO NOT manually edit .iml files via JetBrains MCP** - The IDE's automatic converter runs concurrently and can interfere with edits.

When you need to fix missing dependencies:
1. Use the standard `Edit` tool (not `mcp__jetbrains__replace_text_in_file`) for .iml files
2. The converter will automatically update BUILD.bazel files
3. Verify compilation with `./bazel-build-all.cmd`
