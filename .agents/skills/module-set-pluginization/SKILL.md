---
name: module-set-pluginization
description: Convert Product DSL module sets into bundled wrapper plugins.
---

# Module Set Pluginization

The platform modules to pluginize live inside an aggregate Product DSL `ModuleSet`, typically `essential`, one of the feature sets that `essential` nests, or `ide.common`.

The wrapper is a hand-written JPS module next to the feature modules. It is not auto-generated under `community/module-set-plugins/generated/`. Reference example: `community/platform/navbar/plugin/` (IJPL-245430, commit `9d61ae6803221`).

## Before Editing

- Decide whether the modules should still be inlined inside a parent module set in any product.
- For library modules with only a small closed consumer set, do **not** create or keep a shared wrapper just to make the library available. Prefer `visibility="private"` on the library descriptor and register the module as unnamed `<content>` in each consuming plugin, so each copy lives in that plugin's implicit private namespace.

## Create the Wrapper Plugin Module

Create a new JPS module at `<feature-root>/plugin/`, e.g. `community/platform/<feature>/plugin/`:

- `intellij.platform.<feature>.plugin.iml` under `community/platform/` — resources-only `JAVA_MODULE` inheriting JDK; for example:

  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <module type="JAVA_MODULE" version="4">
    <component name="NewModuleRootManager" inherit-compiler-output="true">
      <exclude-output />
      <content url="file://$MODULE_DIR$">
        <sourceFolder url="file://$MODULE_DIR$/resources" type="java-resource" />
      </content>
      <orderEntry type="inheritedJdk" />
      <orderEntry type="sourceFolder" forTests="false" />
    </component>
  </module>
  ```

- `resources/META-INF/plugin.xml` — a minimal `<idea-plugin>` listing the content modules. Mirror the wrapper shape from navbar and use the canonical `com.intellij.*` plugin id (replace `FEATURE` with the actual short name; the description must be at least 40 characters):

  ```text
  <idea-plugin>
    <id>com.intellij.platform.FEATURE</id>
    <name>FEATURE</name>
    <description>Provides FEATURE platform modules for the IDE.</description>
    <vendor>JetBrains</vendor>
    <content namespace="jetbrains">
      <module name="intellij.platform.FEATURE" loading="required"/>
      <module name="intellij.platform.FEATURE.backend"/>
      <module name="intellij.platform.FEATURE.frontend"/>
      <module name="intellij.platform.FEATURE.monolith"/>
    </content>
  </idea-plugin>
  ```

  **Naming:** the plugin **id** follows the `com.intellij.*` scheme and is **not** the JPS module name. Use `com.intellij.platform.FEATURE` when the wrapper is platform infrastructure that does nothing on its own and other plugins build on top of it (for example `com.intellij.platform.vcs`, `com.intellij.platform.ssh`, `com.intellij.platform.testRunner`), and `com.intellij.FEATURE` when it is a self-sufficient user-facing feature (for example `com.intellij.bookmarks`, `com.intellij.todo`, `com.intellij.navbar`). Check that the id is free first, it could've been used for another plugin. Do **not** use module-name-style ids (`intellij.platform.FEATURE.plugin`, `intellij.FEATURE.plugin`). Once an id has shipped in a release, any rename must keep the old id as an alias. Do **not** reuse the legacy `com.intellij.moduleSet.FEATURE` prefix. The plugin **name** is a short human-readable label (e.g. `Tasks and Contexts`).

  Mark **only the shared/anchor module** (the one that loads in every product mode) as `loading="required"` — `intellij.platform.FEATURE` in the example. Plugin-XML inspection requires at least one required/embedded/required-if-available content module per wrapper, so the anchor satisfies that. Do **not** add `loading="required"` to backend / frontend / monolith modules: in frontend product mode (JetBrains Client) the platform's backend module is unavailable, so a required `FEATURE.backend` excludes itself and takes the whole wrapper plugin down — producing a confusing cascade like "Plugin 'FEATURE' cannot be loaded because it depends on plugin 'IDEA CORE' which failed to load".

- A conventional wrapper needs no separate packaging file. The dev distribution derives each member's jar from the plugin's `<content>`, the loading rule and the member's `package` attribute.

  `plugin-model-tool` derives the platform layout and compatible published plugins from sources. It writes the plugin's jars, members, and libraries into the `dev` section of the plugin's `BUILD.bazel`. Do not edit generated sections or plan files. `AllProductsPackagingTest` compares the derivation with the build layout and packed jars. Closed-product dist-file observations live in ultimate, at each product's `PackagingTargetSpec.contentYamlPath`, for example `dbe/build/datagrip-content.yaml`. Packaging tests report changes as patches for review. These YAML files are test expectations, not generator or assembly inputs.

- Suppress `SplitModeMixedDependencies` and `PluginXmlPluginLogo` in `plugin.xml` only if the inspection actually flags the wrapper.

## Update the Product DSL

Remove the now-pluginized modules from the aggregate `ModuleSet`:

- In `community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt` (and any product-specific layout file that listed them), delete the `module("intellij.platform.<feature>.…")` / `embeddedModule(...)` calls that the wrapper now owns.
- Add the wrapper JPS module name to `DEFAULT_BUNDLED_PLUGINS` in `community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/ProductModulesLayout.kt`, e.g. `"intellij.platform.<feature>.plugin"`.
- Add the wrapper JPS module name to `HAND_WRITTEN_MODULE_SET_PLUGIN_MODULES` in `community/platform/build-scripts/product-dsl/src/ModuleSetPlugins.kt`.
- Check products that override or reset default bundled plugins (Rider's `DotnetExternalProductProperties` in `ReSharperExternalProductProperties.kt`, Gateway, JetBrains Client product-modules XML under `remote-dev/`, etc.) and add the wrapper module name where appropriate.

## Re-trim Content Module Descriptors

- Trim `intellij.platform.<feature>.<backend|frontend|monolith>.xml` so it declares the actual runtime dependencies (e.g. `intellij.platform.backend`, `intellij.platform.frontend`, the shared `intellij.platform.<feature>` module).
- If any extension point was renamed/qualified, search the repository for the old short name with `./community/tools/accio.cmd locate '<old short name>'` and update every `<extensionPoint …>` reference accordingly.
- If a downstream consumer used a module that the new wrapper now hides, switch the consumer's descriptor to depend on the wrapper plugin: `<plugin id="com.intellij.platform.<feature>"/>` instead of `<module name="intellij.platform.<feature>.frontend"/>`.

## Re-run Code Generation

After the source edits, run the generators so the platform descriptors and Bazel files are updated to match:

```bash
./build/jpsModelToBazel.cmd
./bazel.cmd run //platform/buildScripts:plugin-model-tool
```

Expected diffs:

- `community/platform/platform-resources/generated/META-INF/intellij.moduleSets.essential.xml`, `…ide.common.xml`, and `licenseCommon/generated/META-INF/intellij.moduleSets.ide.ultimate.xml` no longer list the pluginized modules.
- `.idea/modules.xml` gains the new `intellij.platform.<feature>.plugin.iml` module in canonical order. For a community wrapper, `community/.idea/modules.xml` gains it too.
- `community/build/bazel-generated-file-list.txt` gains the new plugin directory of a community wrapper, and `build/bazel-generated-file-list.txt` gains that of an ultimate wrapper.
- The wrapper plugin's `BUILD.bazel` gains a `dev` section with its `dist_plugin` call, written by `plugin-model-tool`.
- `tests/ideaProjectStructure/testResources/com/intellij/ideaProjectStructure/fast/available-in-idea-free-mode.txt` gains the plugin id of an ultimate wrapper if it is free-mode available.
- Existing wrappers under `community/module-set-plugins/generated/intellij.moduleSet.plugin.<name>/` are unrelated — do not touch them unless you are migrating one to a hand-written wrapper.

## Downstream Test Runtime Dependencies

Pluginization can break narrow test modules even when product packaging tests pass. Test runtime classpaths may still include a plugin module whose extracted platform dependency is no longer embedded. Catch this from `idea.log`, not from test assertion text alone.

- Inspect `Plugin set resolution` and `Problems found while loading plugins` in local or TeamCity test logs.
- Treat messages such as missing `intellij.platform.structureView.impl`, `intellij.platform.execution.serviceView`, or `intellij.platform.navbar.frontend` as a missing wrapper-plugin runtime dependency (`intellij.platform.structureView.plugin`, `intellij.platform.execution.serviceView.plugin`, `intellij.platform.navbar.plugin`, or the new `intellij.platform.<feature>.plugin`).
- Add the wrapper dep only to the affected test module with `scope="RUNTIME"`. Do not add it to broad shared modules such as `intellij.platform.testFramework` or aggregate/global test infrastructure.
- For TeamCity, read the published test log artifact when needed: `/app/rest/builds/id:<buildId>/artifacts/content/testlog.zip!.../idea.log`.

## Pre-TeamCity Canary Suite

Run this suite before relying on TeamCity after pluginization or plugin dependency changes. Run the listed commands in parallel when resources allow; the order is diagnostic priority, not execution order. Fix failures in checks 1-6 before interpreting downstream canary failures.

1. Embedded dependency closure:
   `./bazel.cmd run //platform/buildScripts:plugin-model-tool -- --json='{"filter":"embeddedDependencyClosure","pluginSourceOnly":true}'`
2. Product DSL module-set validation:
   `./community/tools/bt.cmd --module intellij.platform.buildScripts.productDsl.tests --filter org.jetbrains.intellij.build.productLayout.validator.ProductModuleSetValidatorTest`
3. Fast project structure/root packages:
   `./tests.cmd --module intellij.projectStructureTests --test com.intellij.ideaProjectStructure.fast.IntelliJProjectPackageNamesTest`
4. Product packaging baseline:
   `./bazel.cmd test //build/tests/smoke:all-products-packaging_test`
5. CLion packaging baseline:
   `./tests.cmd --module intellij.clion.build.tests --test org.jetbrains.intellij.build.clion.CLionPackagingTest`
6. Rider packaging baseline:
   `./tests.cmd --module intellij.rider.build.tests --test com.jetbrains.rider.build.RiderPackagingTest`
7. Database and SQL plugin loading:
   `./tests.cmd --module intellij.database.tests.aggregator --test com.intellij.database.DatabaseTestSuite`
   `./tests.cmd --module intellij.database.sql.tests --test com.intellij.sql.SqlFileStructureViewTest`
   `./tests.cmd --module intellij.database.sql.tests --test com.intellij.sql.editor.SqlMultiLineTodoTest`
8. Code Server DB plugin loading:
   `./tests.cmd --module intellij.codeServer.db.sql.tests --test com.intellij.database.SqlCompletionTest`
   `./tests.cmd --module intellij.codeServer.db.csv.datagrid.tests --test com.intellij.codeServer.db.csv.datagrid.tests.CsvCoreGridTest`
9. Package Checker plugin loading:
   `./tests.cmd --module intellij.packageChecker.tests --test com.intellij.packageChecker.model.VulnerabilitiesRepositoryServerTest`
10. Grazie and spellchecker plugin loading:
   `./community/tools/bt.cmd --module intellij.grazie.tests --filter com.intellij.grazie.spellchecker.inspector.SuggestionTest`
   `./community/tools/bt.cmd --module intellij.grazie.tests --filter com.intellij.grazie.spellchecker.inspection.CommentsWithMistakesInspectionTest`
11. Shell plus Markdown plugin loading:
   `./community/tools/bt.cmd --module intellij.sh.tests --filter com.intellij.sh.highlighting.ShHighlightUsagesInMarkdownTest`
12. Ruby plugin loading:
   `./tests.cmd --module intellij.ruby.tests --test org.jetbrains.plugins.ruby.gem.GemsProviderTest`
   `./tests.cmd --module intellij.ruby.tests --test org.jetbrains.plugins.ruby.ruby.psi.RubyHeredocInjectorTest#testSqlHeredoc`
13. RustRover structure and SQL loading:
   `./tests.cmd --module intellij.rustrover.core.test --test org.rust.ide.structure.RsStructureViewModelTest`
   `./tests.cmd --module intellij.rustrover.sql.tests --test com.jetbrains.rust.sql.SqlInjectorTest`
14. ReSharper external services/DataGrip bridge:
      `./tests.cmd --module intellij.resharper.external.services.test.cases.rd --test com.jetbrains.resharper.external.services.test.cases.ReSharperDataGripRdTest`
15. AI Assistant plugin loading:
    `./tests.cmd --module intellij.ml.llm.chat.tests --test com.intellij.ml.llm.core.chat.ui.chat.navigation.FilePathMatcherTest`
    `./tests.cmd --module intellij.ml.llm.completion.tests --test com.intellij.ml.llm.completion.cloud.context.mainEditor.RecentLocationsContextCollectorTest`
    `./tests.cmd --module intellij.ml.llm.devkit.tests --test com.intellij.ml.llm.devkit.context.DevKitChatContextProviderTest`
    `./tests.cmd --module intellij.ml.llm.java.embeddings.tests --test com.intellij.ml.llm.java.embeddings.JavaEmbeddingParsingTest`
    `./tests.cmd --module intellij.ml.llm.java.inlinePromptDetector.tests --test com.intellij.ml.llm.java.inlinePromptDetector.JavaInlinePromptHighlightingTest`
    `./tests.cmd --module intellij.ml.llm.nextEdits.tests --test 'com.intellij.ml.llm.nextEdits.tests.history.NextEditFileHistoryTest;com.intellij.ml.llm.nextEdits.tests.diff.CheckFileRangesSyntaxCorrectTest'`
    `./tests.cmd --module intellij.ml.llm.ruby.test --test com.intellij.ml.llm.ruby.testGeneration.RubyLLMGenerateTestsSupportPromptTest`
    `./tests.cmd --module intellij.ml.llm.ruby.test --test com.intellij.ml.llm.ruby.testGeneration.rails.RailsGenerateTestsPromptTest`
    `./tests.cmd --module intellij.ml.llm.sql.tests --test com.intellij.ml.llm.sql.SqlObjectChatInputTest`

## Verification

- Run the Pre-TeamCity Canary Suite after generated files and narrow test dependencies are updated.
- Keep CLion and Rider packaging tests in the standard suite: wrapper plugins and `DEFAULT_BUNDLED_PLUGINS` changes can affect those product baselines even when `AllProductsPackagingTest` is green.

- Run at least one representative test from each affected test module. Prefer a small smoke test that exercises plugin loading without external fixtures.
- If the original CI test needs unavailable data or services, run a nearby smoke test and document the local blocker. Confirm `idea.log` no longer reports the original missing wrapper plugin.
