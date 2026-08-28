# Module In Multiple Plugins Validation

Entry point: `collectModulesInMultiplePlugins` in `platform/buildScripts/src/productLayout/ultimateGenerator.kt`.
This rule is no `PipelineNode`, so it has no `NodeId`.

## Overview

A plugin packs a JPS module through `spec.withModule`, and `BaseLayout.includedModules` holds the entry. The plugin
then owns a private copy of the module classes. Two plugins that pack one module hold two copies. The copies grow the
distribution, and a third plugin that depends on both plugins loads one class from two classloaders. The JVM raises a
`LinkageError` on the second class.

`spec.withModule` is on the way out. The Product DSL replaces it with a content module declaration, which the plugin
graph models and the other validators read. So this rule is a ratchet on a dying mechanism: it holds the list of such
an entry still, and it lets the migration remove one entry at a time.

`IdeaUltimatePluginStructureTest.modules included in multiple plugins` held this rule first. That test sat outside the
`build-and-verify` list, and only `IdeaUltimateBuildTest` on CI ran it, so a change that broke it went unnoticed. The
rule now rides the generator, which every `plugin-model-tool` run and every packaging test starts.

The rule cannot live in a `productDsl` validator. A static layout entry is no content edge, so the plugin graph holds
no node for it: `moduleInfo` for `intellij.javaee.jax.ws.utils` reports no owner at all. And `PluginLayout` is a
build-script type that `productDsl` cannot name. `collectPluginVariantOverlaps` sits in the same file for the same two
reasons.

## Inputs

- `ModuleSetGenerationConfig.discoveredProducts`, for the `ProductProperties` of each product.
- `ProductProperties.productLayout.pluginLayouts`, which is the registry of the non-trivial plugin layouts of that
  product.
- `BaseLayout.includedModules` of each layout, which is a `Collection<ModuleItem>`. The rule reads `moduleName` and
  `relativeOutputFile`.

The rule reads no `plugin.xml` and no content declaration. It also reads no `BuildContext`, so it costs one pass over
data the generator already holds.

## Rules

- For each discovered product, build a map from a JPS module name to the set of plugin main modules that pack it.
  - A plugin is declared once for each supported os and arch, so the main module collapses the variants of one plugin.
    One plugin is therefore one owner. `collectPluginVariantOverlaps` groups by the same key.
  - Count a `ModuleItem` only when `relativeOutputFile` holds no `/`. That path is relative to `lib/`, so a value with
    `/` names a jar below `lib/`, and such a jar keeps its own classloader. The earlier guard used the same test, and
    `SarifSingleCopyValidation` reads the same directory for the same reason.
  - Keep a name that two or more distinct owners pack.
- Merge the names of every product, and report each name once.
  - The pairing happens inside one registry, and the report happens across all of them. Both halves matter. Many
    products share one registry, so one report per product would repeat one answer many times. And a pair drawn across
    two registries is no pair at all: `intellij.kotlin.plugin` and `language-server.plugins.kotlin` pack the same
    modules, and no registry holds both.
  - A name that two registries duplicate arrives with the owners of both. That keeps one row for one name.
- Skip a name that the allowlist holds.
- Report an allowlisted name that no registry duplicates. That is a stale entry.

### The inclusion reason is not read

`ModuleIncludeReasons.isProductModule(reason)` marks an inclusion that the product model drives rather than a
hand-written `spec.withModule`. The rule reads no reason, and the measurement is the argument.

Both options were measured on the whole model, with the allowlist empty. Each option reported the same names, for
every one of the discovered products, and the two outputs are equal line for line. The cause is structural:
`processProductModule` is the only producer of a product-module reason, and it writes into a `PlatformLayout`. A
`PluginLayout` therefore holds no such entry, and a test on the reason can change no report. A test would also need a
copy of `ModuleIncludeReasons`, which is internal to the `intellij.platform.buildScripts` module.

## Suppression and allowlists

`ErrorCategory.MODULE_IN_MULTIPLE_PLUGINS` carries no `suppressionKey`. The allowlist is the whole suppression
mechanism, and `KNOWN_MODULES_IN_MULTIPLE_PLUGINS` in `platform/buildScripts/src/productLayout/ultimateGenerator.kt`
holds it, beside the rule.

The allowlist does not live in `platform/buildScripts/suppressions.json`, for three reasons.

1. The rule runs outside the `productDsl` pipeline. `Pipeline.aggregate` is the only reader of a `suppressionKey`, so
   an entry of that file would never reach this rule.
2. Every map of that file is keyed by a `ContentModuleName`. The subject here is a JPS module name, and the two
   namespaces are not the same. `contentModuleCopyConflicts` is the closest pattern, and it is keyed by a content
   module name.
3. `SuppressionConfigGenerator` copies such a map without a change, so a stale entry of that file is never reported.
   The stale report is half of this rule.

The allowlist holds two groups, and the group of a name says which guard first saw it.

### The names the earlier guard held

- `intellij.go.utils`, packed by `intellij.go.plugin` and `intellij.go.template`
- `intellij.javaee.jax.ws.utils`, packed by `intellij.javaee.jax.rs` and `intellij.javaee.jax.ws`
- `intellij.javaee.jax.ws.rt`, packed by `intellij.javaee.jax.rs` and `intellij.javaee.jax.ws`
- `intellij.marketplace.statisticsCompat`, packed by `intellij.kmm.plugin` and `intellij.marketplace`
- `intellij.libraries.kotlinc.kotlin.compiler.common`, packed by `intellij.kotlin.jsr223.plugin`,
  `intellij.kotlin.plugin` and `kotlin.frontend.split`

### The names outside the earlier guard's reach

The earlier guard read the packaged content report of IDEA Ultimate, so it saw a bundled plugin and a published plugin
of that one product. Each name below sits in a plugin that IDEA Ultimate neither bundles nor publishes, and the
generator reads the layout registry rather than one packaged product.

[IJPL-254035](https://youtrack.jetbrains.com/issue/IJPL-254035) tracks this group. `spec.withModule` declares each
entry, so an entry drains as the deprecation proceeds. The issue closes when the group is empty.

`intellij.kmm.plugin` packs the AppCode and the CIDR modules of the plugins that own them: `intellij.appcode`,
`intellij.appcode.swift`, `intellij.appcode.swiftDebugger`, `intellij.cidr.cocoa`, `intellij.cidr.cocoaCommon`,
`intellij.cidr.cocoaDevices`, `intellij.cidr.cocoaDevices.debugging`, `intellij.cidr.plist`, `intellij.cidr.strings`,
`intellij.cidr.xcodeModel.core`, `intellij.cidr.xctest`, `intellij.swbuild` and `intellij.swift.packageManager`.

`kotlin.frontend.split` packs `intellij.kotlin.base.codeInsight.minimal` and `intellij.kotlin.highlighting.minimal`,
which `intellij.kotlin.plugin` packs too. The frontend plugin and the backend plugin never share one classloader, so
this pair is a distribution cost alone.

`intellij.python.wsl` names a Windows-only plugin and a module of `intellij.python.plugin`, and each plugin packs the
module.

## Output

- `ModuleInMultiplePluginsError`, at most two per run. The duplicate report carries the context `plugin-layouts` and
  the error ID `module-in-multiple-plugins:plugin-layouts`. The stale report carries the context `allowlist` and the
  error ID `module-in-multiple-plugins:allowlist`.
- The duplicate report names each module and every owning plugin under it. It then states the three fixes: move the
  module to the platform, extract a new plugin, or grandfather the name.
- The stale report names each name that no registry duplicates any more.
- Neither report names a product. Many products share one registry, so a product name would say nothing.
- The rule joins the `commitChanges` guard beside `collectPluginVariantOverlaps`, so a red rule blocks the
  dev-distribution plan write and every XML write of the run.

## Auto-fix

- None. A fix moves code, and only a human can pick the route. The error text holds the three routes and the
  allowlist entry that grandfathers the name.

## Non-goals

- **A content module that two plugins declare with no namespace.** That shape is legal and documented. Read
  `docs/IntelliJ-Platform/4_man/Plugin-Model/Including-content-module-in-multiple-plugins.md`, which is IJPL-A-1893.
  `PluginModuleId.toActualId` gives each such copy an implicit namespace per plugin, so the runtime IDs do not collide.
  This rule reads no content declaration, so it can never report that shape. An earlier draft of the packaging guard
  did read one, and it reported the IJPL-A-1893 example `intellij.libraries.assertj.core` and the permanent
  `intellij.platform.commercial.verifier`.
- **The classloader half of the question.** `ContentModuleCopyConflictValidator` answers whether a content module can
  reach two embedded copies of one name. This rule answers only whether two plugins pack one module.
- **A jar below `lib/`.** Such a jar keeps its own classloader, and the rule states no verdict there.
- **A module that only the packaging run adds.** `autoLayout` computes a plugin's content modules from its
  `plugin.xml`, and the generator holds no such entry. The rule reads the static layout alone.
- **The platform as an owner.** The earlier guard counted `com.intellij` as one owner, because
  `PlatformLayout.includedModules` holds the product modules. This rule reads the plugin layouts alone, so a module
  that the platform and one plugin both hold is out of scope.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [content-module-copy-conflict.md](content-module-copy-conflict.md)
