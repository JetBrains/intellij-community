# Product DSL

Rules for changing anything under `community/platform/build-scripts/product-dsl/`.

## Testing Guidelines

- **Use AssertJ** for assertions in tests (`org.assertj.core.api.Assertions.assertThat`)
- Prefer `containsExactlyInAnyOrder()` for set comparisons
- Prefer `isEmpty()`, `isTrue()`, `isFalse()` over JUnit assertions

## Verification Commands

Both commands below need the ultimate workspace: the generator target and the run configuration are
defined there, not in a community-only checkout.

1. **Run Generator** (performs compilation via Bazel — no extra compilation step needed):
   ```bash
   bazel run //platform/buildScripts:plugin-model-tool
   ```

   **Verification — must produce no changes:**
   - Do NOT just trust the generator's printed stats
   - Run `git status` to verify no XML files were modified on disk
   - If unexpected changes appear:
     - **Stop immediately** — do not proceed with the task
     - Ask the user what to do (changes may indicate a problem or require acceptance criteria update)
   - Expected changes (e.g., when modifying related functionality) should be documented in acceptance criteria beforehand

2. **Run Tests**:
   ```
   mcp__ijproxy__execute_run_configuration(configurationName="Plugin DSL Tests", waitForExit=true)
   ```
   - **Exit code 0**: All tests passed, no further action needed
   - **Exit code non-zero**: Read `out/product-dsl-test-failures.log` for compact failure details

  The "Plugin DSL Tests" run configuration has `TEST_FAILURE_LOG=out` (project-relative) preset.

  Without a running IDE, the same package goes through the command-line runner:
   ```bash
   ./tests.cmd --module intellij.platform.buildScripts.productDsl.tests --test "org.jetbrains.intellij.build.productLayout.*"
   ```

## Key Entry Points

- `UltimateGenerator` (`platform/buildScripts/src/productLayout/ultimateGenerator.kt`) — the generator;
  it is the `main_class` of the Bazel target below.
- `CommunityModuleSets` (`community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt`)
  — the community-side module-set definitions the generator reads. It has no `main()`.
- Bazel: `//platform/buildScripts:plugin-model-tool`

## Debugging

### Debug Output (`--log`)

Enable debug output via `--log` argument:
- `--log` or `--log=*` — all debug output
- `--log=filterDeps` — only specific tags
- `--log=filterDeps,graph` — multiple tags

Run generator with debug:
```bash
bazel run //platform/buildScripts:plugin-model-tool -- --log
bazel run //platform/buildScripts:plugin-model-tool -- --log=filterDeps
```

### Adding Debug Statements

Use the `debug()` function when investigating issues:

```kotlin
import org.jetbrains.intellij.build.productLayout.debug

// Untagged - always outputs when debug enabled
debug { "processing module=$moduleName" }

// Tagged - outputs when tag matches or filter is "*"
debug("filterDeps") { "plugin=$name deps=${deps.size}" }
```

**Note:** Keep useful debug statements in the code after investigation. They're zero-cost when disabled and valuable for future debugging.

### PluginGraphDebug Utilities (plugin-graph module)

For interactive PluginGraph analysis, use `PluginGraphDebug` (separate module, always outputs `DEBUG:` prefix):

```kotlin
import com.intellij.pluginGraph.PluginGraphDebug

with(PluginGraphDebug) {
  pluginGraph.traceDependencyPath("intellij.platform.lang", "intellij.libraries.hamcrest")
  printModuleDeps(pluginGraph, "intellij.platform.lang")
  printProductModules(pluginGraph, "IDEA")
  printContainingPlugins(pluginGraph, "intellij.platform.lang")
  compareProdVsTestDeps(pluginGraph, "intellij.platform.lang")
}
```

## Documentation

See [README.md](./README.md) for architecture overview and [docs/](./docs/) for detailed documentation.

Key specs to consult when changing behavior:
- [docs/test-plugins.md](./docs/test-plugins.md) (automatic dependency addition, DSL test plugin spec expansion)
- [docs/dependency_generation.md](./docs/dependency_generation.md) (PluginGraph as source of truth, library module mapping)
- [docs/validation-rules.md](./docs/validation-rules.md) (validation rules and terminology)

## Terminology

- **Content module**: a module declared in plugin.xml `<content>` or in module sets/products; has a descriptor and participates in dependency validation.
- **Target/JPS module**: build-time module from `.iml` / Bazel target; used as input to generation, not validation.
- **Plugin XML `<module>` dependency**: refers to a **content module**; use “content” wording in validator names and messages to avoid ambiguity.
