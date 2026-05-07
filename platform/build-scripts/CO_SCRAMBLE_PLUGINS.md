# Co-Scrambled Plugins

## Overview

This document describes how the IntelliJ build scripts scramble certain bundled plugins **in
the same ZKM run as the platform**, instead of scrambling them in a separate per-plugin pass.

**Related Issue**: IJPL-231137

**Related Files**:
- `impl/PluginLayout.kt` — `scrambleWithPlatform()` DSL flag.
- `ScrambleTool.kt` — `scramble(... coScrambleEntries, classpathDirs ...)` API + `CoScrambleEntry` class.
- `impl/DistributionJARsBuilder.kt` — production orchestration; `collectCoScrambleEntries`, `collectAllPluginClasspathDirs`.
- `impl/plugins/pluginBuilder.kt` — `scrambleAlreadyLaidOutPlugins`, `buildPlugins(layoutOnly = …)`.
- `impl/plugins/bundledPluginBuilder.kt` — `buildBundledPluginsForAllPlatforms(layoutOnly = …)`, `writeBundledPluginInfoAfterScramble`.
- `dev/PluginBuilder.kt` + `dev/IdeBuilder.kt` — dev-mode mirror.
- `zkm/ZkmScrambleTool.kt` — single-pass scramble that takes platform jars + co-scramble plugin jars + classpath dirs + include fragments.

## The Problem

Some plugins (currently `intellij.platform.frontend.split.plugin`) ship code — frequently
reflective lookups inside frameworks like RD — that calls into platform classes the platform
ZKM run renames. With the previous design these plugins were scrambled in a **second** ZKM
invocation that consumed the platform's `ChangeLog.txt` and tried to keep references in sync.

That two-pass scheme is fragile. ZKM features that depend on the call graph in scope (most
visibly `methodParameterChanges=flowObfuscate`) compute different results between the two
runs even with the same seed. The platform on disk and the references inside the plugin
end up using subtly different signatures, producing runtime failures like:

```
NoSuchMethodException in com.intellij.ide.X.Y.Z l(int, char, int)
```

The flags `-DZKM_METHOD_PARAM_SYNCH` and `-DZKM_METHOD_PARAM_CHANGING_LOOKUP` improve
synchronization for direct call sites, but ZKM's reflection-string rewriting is a separate
code path that does not consult the input ChangeLog. Several rounds of fixes there did not
fully eliminate the divergence.

## The Fix

There is **one ZKM run = one source of truth** for scrambled mappings. Plugins that need
cross-references with platform classes opt into that single run via
`PluginLayoutSpec.scrambleWithPlatform()`. Their `pathsToScramble` jars are added to the
platform's `__SCRAMBLED_CLASSES__` list and rewritten in place under their plugin target
directory.

```kotlin
fun layoutRemoteDevFrontend(): PluginLayout = PluginLayout.pluginAutoWithCustomDirName(...) {
  it.scramble("lib/${it.mainJarName}")
  it.scramble("lib/modules/intellij.platform.frontend.split.startup.jar")
  // …
  it.coScrambleZkmScriptInclude("../platform/split/build/script.zkm.include")
  it.scrambleWithPlatform()                    // ← join the platform ZKM run
}
```

`scrambleWithPlatform = true` does two things:
1. The plugin's `pathsToScramble` jars become inputs of the platform ZKM run.
2. The plugin's per-plugin `scramblePlugin(...)` call short-circuits — it would be redundant
   work and would re-randomize ZKM choices.

The plugin's `zkmScriptStub(...)` is used only for normal per-plugin scrambling and cannot be combined
with `scrambleWithPlatform()`. If the co-scrambled jars need extra ZKM rules in the platform run,
provide a small fragment via `coScrambleZkmScriptInclude(...)`. The product/platform script must expose
the `__SCRAMBLE-INCLUDE__` placeholder; otherwise the build fails before moving any jars.

## Build Flow

The orchestrator (`buildPlatformAndPlugins` in production, `IdeBuilder` in dev) runs four
phases now instead of three:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. Lay out platform lib (buildLib)                                          │
└─────────────────────────────────────────────────────────────────────────────┘
                              ╲                ╱
                               ╲              ╱
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. Lay out ALL bundled plugins (buildBundledPluginsForAllPlatforms          │
│    with layoutOnly = true) — in parallel with phase 1                       │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. ONE ZKM run                                                              │
│    open  = platform jarFilesToScramble + every co-scramble plugin's        │
│            pathsToScramble jars                                             │
│    script = product/platform ZKM stub + coScrambleZkmScriptInclude fragments│
│    classpath = platform classpath ∪ jrt-fs ∪ JUnit4 ∪                       │
│                walk(plugins[*]/lib) recursively for every dir               │
│    scrambled outputs go back to the same on-disk paths                      │
│    (lib/foo.jar stays in lib/, plugin jars stay under plugins/<dir>/lib/)   │
│    Per-plugin descriptor cache + .BACKUP cleanup runs for each              │
│    co-scramble plugin, mirroring runScrambler's pluginContext branch        │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. Per-plugin scramble for the rest (scrambleAlreadyLaidOutPlugins)         │
│    Co-scramble plugins are skipped — already done in phase 3                │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. writePluginInfo (writeBundledPluginInfoAfterScramble) so                 │
│    plugin-classpath.txt reflects scrambled descriptors                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

Two ordering invariants matter:

- Phase 2 must finish before phase 3 — the platform ZKM run needs every plugin's
  `lib/...` on its classpath so trim/obfuscate can resolve cross-plugin references
  (e.g. `SessionListener` in the frontend split plugin extends `ClientSessionListener`
  in the cwm plugin's `lib/frontend-split/rd-client-base.jar`).
- Phase 5 must run **after** phase 3 + 4. `plugin-classpath.txt` is generated from the
  descriptor cache, and the cache is updated to its scrambled form during phases 3 and 4.

## Why ZKM Needs the Whole Plugin Set on Classpath

In the previous per-plugin scrambles, the *open* set was just one plugin's jars and ZKM
analyzed inheritance hierarchies only within that small scope. Cross-plugin parents
(`ClientSessionListener` in cwm, parent of `SessionListener` in frontend split) were never
walked, so their absence on the classpath was tolerated.

The single co-scramble run opens platform jars *and* the co-scramble plugin jars together.
ZKM walks the inheritance hierarchies of all opened classes for hierarchy-aware renaming
and now requires every transitively referenced parent to be resolvable — even if it lives
in a plugin that is not being scrambled.

`collectAllPluginClasspathDirs` therefore recursively walks each laid-out plugin's
`lib/` directory and adds every subdirectory (`lib/`, `lib/modules/`, plugin-specific
subdirs like `lib/frontend-split/`) to the ZKM classpath. The walk reads directory entries
only — no I/O on jar contents — and is short-circuited (`emptyList()`) when no plugin opts
into the co-scramble run, so products with no co-scramble plugins pay nothing.

## Cost & Limits

- ZKM scales with input size. Bundling N additional plugin jars into the platform run
  roughly increases its input size by `sum(co-scramble pluginsʼ pathsToScramble jar sizes)`.
  Today only one plugin opts in and the cost is negligible. If the opt-in list grows large,
  consider per-product gating.
- Per-plugin descriptor-cache update for co-scramble plugins runs **inside** the platform
  scramble step (see `scramble(...)` in `ZkmScrambleTool.kt`). It mirrors the post-scramble
  bookkeeping `runScrambler` does for a `pluginContext != null`.
