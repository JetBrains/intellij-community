---
name: pseudo-kmp
description: Create or modify IntelliJ pseudo-KMP expect/actual modules.
---

# Pseudo-KMP Modules

The monorepo is a JPS-based project, and JPS does not support Kotlin Multiplatform. The `expects-compiler-plugin` emulates the KMP expect/actual declarations.

## Documentation

- [Multiplatform Migration Guide](../../../../docs/IntelliJ-Platform/4_man/Mutliplatform-Migration.md) — step-by-step migration instructions
- [Multiplatform Support README](../../../platform/multiplatformSupport/README.md) — compiler plugin usage
- [ExpectInMultiplatform.kt](../../../platform/multiplatformSupport/src/fleet/util/multiplatform/ExpectInMultiplatform.kt) — `linkToActual()` and `@Actual` API docs

## Module Configuration Checklist

1. **Register in the structure test.** Add your module name to the `multiplatformModules` list in `com.intellij.ideaProjectStructure.fast.MultiplatformModuleStructureTest` (`tests/ideaProjectStructure/testSrc/com/intellij/ideaProjectStructure/fast/MultiplatformModuleStructureTest.kt`).

2. **Use only allowed library dependencies.** The test enforces a closed set: `allowedMultiplatformLibraryDependencies`. If you need a new library, add it to this list. Dependencies on other multiplatform modules (those in `multiplatformModules`) are also allowed.

3. **Set up source folders:**
   - `src/` — common code
   - `srcJvm/` or `srcJvmMain/` — JVM-specific code (Java code can live here too)
   - `srcWasmJs/` or `srcWasmJsMain/` — WasmJs-specific code
   - `srcJsMain/` — JS-specific code
   - `srcNativeMain/` — Native-specific code

   Do NOT add `srcWasmJs`/`srcWasmJsMain` as a source root in the `.iml` file. The IDE would try to compile it in JVM mode.

4. **Add the `.pseudoCommonKotlinSourceSet` marker file** to the common source root (e.g., `src/.pseudoCommonKotlinSourceSet`). This empty file tells the Kotlin plugin that the source set is common code, preventing Optimize Imports from removing imports that are unnecessary on JVM but mandatory in common code (e.g., `@JvmStatic`, `@JvmField`).

## IML File Setup

Use `community/platform/util/multiplatform/intellij.platform.util.multiplatform.iml` as a reference.

Add the support module dependency (PROVIDED scope):

```xml
<orderEntry type="module" module-name="intellij.platform.multiplatformSupport" scope="PROVIDED" />
```

The Bazel target of the support module exports the `expects-compiler-plugin` to each module that depends on it. The Kotlin facet of the `.iml` has no `pluginClasspaths` entry for the plugin.

## Expect/Actual Pattern

Only **top-level functions** are supported. Both the expect and actual declarations must be in the **same module**.

### Common side (in `src/`)

Use `linkToActual()` as the function body:

```kotlin
import fleet.util.multiplatform.linkToActual

internal fun myFunction(param1: String, param2: Int): Boolean = linkToActual()
```

### Platform side (in `srcJvm/`, `srcWasmJs/`, etc.)

Annotate with `@Actual`. The function name must be the expect function name + platform suffix:

```kotlin
import fleet.util.multiplatform.Actual

@Actual
internal fun myFunctionJvm(param1: String, param2: Int): Boolean {
  // JVM implementation
}
```

### Platform suffixes

| Platform                    | Suffix   |
|-----------------------------|----------|
| JVM                         | `Jvm`    |
| WasmJs                      | `WasmJs` |
| JS                          | `Js`     |
| Native                      | `Native` |
| Multi-platform build (rare) | `Impl`   |

### Real example

- Common: `getCharsPlatformSpecific` in `community/platform/util/base/multiplatform/src/com/intellij/util/text/CharArrayUtilKmp.kt`
- JVM actual: `getCharsPlatformSpecificJvm` in `community/platform/util/base/multiplatform/srcJvmMain/com/intellij/util/text/jvm.kt`

## Code Conversion Notes

- The monorepo uses the **JVM version** of the Kotlin standard library, which differs from the common version. Check which declarations are available in common code.
- `@JvmStatic`, `@JvmField`, and similar annotations are **not** auto-imported in common code. Import them manually.

## After Editing

Run `MultiplatformModuleStructureTest` to validate module structure:

```bash
./tests.cmd --module intellij.projectStructureTests --test com.intellij.ideaProjectStructure.fast.MultiplatformModuleStructureTest
```

## Artifact Publication

The monorepo does not publish multiplatform artifacts to Maven Central directly. Separate repositories with Gradle infrastructure extract module sources, compile with true KMP, and publish. See `fleet-multiplatform-deps` and `fleet-parser-collection` in JetBrains Space.
