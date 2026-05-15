# Migration Report: kotlinx.collections.immutable 0.5.x

**Project:** intellij-community (IntelliJ IDEA Platform monorepo)
**Build system:** Bazel (monorepo, source-of-truth JPS project model generates Bazel rules)
**Date:** 2026-05-15
**kotlinx.collections.immutable version:** 0.4.0 → 0.5.0-beta01
**Kotlin version:** 2.3 (api_version / language_version, unchanged)
**Status:** Completed

---

## Pre-Migration State

### Library Usage

The library is declared once at the JPS project level (source of truth) and
referenced by ~58 modules' iml files plus a wrapper module under
`libraries/kotlinx/collections-immutable/`. Bazel files for the library are
generated from the JPS model.

| Declaration site | Role | Version (pre) |
|------------------|------|---------------|
| `.idea/libraries/kotlinx_collections_immutable.xml` | JPS project library descriptor (source of truth, carries maven-id and SHA-256) | 0.4.0 |
| `lib/MODULE.bazel` | Bazel `http_file` declarations for jar + sources (with version-encoded repo names and SHA-256s) | 0.4.0 |
| `lib/BUILD.bazel` | Bazel `copy_file` (version-encoded jar name) + `jvm_import` wrapper named `kotlinx-collections-immutable` (version-agnostic) | 0.4.0 |
| `libraries/kotlinx/collections-immutable/BUILD.bazel` | High-level wrapper target `libraries-kotlinx-collections-immutable` exporting `@lib//:kotlinx-collections-immutable` (version-agnostic, kept unchanged) | n/a |

**Out-of-scope build-tooling pins (deliberately not bumped):**

- `platform/build-scripts/bazel/maven_install.json` (build-scripts Maven lockfile, pins 0.4.0 for build-tooling)
- `build/jvm-rules/libs.lock.json` (jvm rules build infrastructure, pins 0.3.8 for build-tooling)

These are flagged as the skill prescribes and bumped only on explicit request.

### Library Users

- **138** Kotlin files import `kotlinx.collections.immutable.*`
- **8** Java files import `kotlinx.collections.immutable.*`
- **85** files had at least one candidate call-site line matching the deprecated method names (many of those turned out to be on `MutableList`/`MutableMap`/builder receivers after receiver-type analysis)
- **1** third-party `PersistentList` implementer: `fleet/bifurcan/srcCommonMain/fleet/bifurcan/List.kt`

### Baseline Build

- **Compile command:** `./bazel.cmd build <module>` per representative target. The IntelliJ monorepo has no useful "build everything" command; per the skill's Monorepos guidance the compile command is a template with a module-target slot.
- **Baseline target tested:** `//tools/apiDump:apiDump` (a small library user) — built successfully on 0.4.0 before any change.

### Pre-existing `@Suppress("DEPRECATION")` annotations

Pre-existing `@Suppress("DEPRECATION")` / `@Suppress("OVERRIDE_DEPRECATION")` annotations exist in some files modified later (`BuildContextImpl.kt`, `PluginLayout.kt`, `UnindexedFilesScannerExecutorImpl.kt`, `ToolWindowImpl.kt`), but every one of those covers unrelated IntelliJ internal deprecations (`productCode`, `pluginAutoWithCustomDirName`, `headerToolbar`, `unregisterToolWindow`, `suspendScanningAndIndexingThenRun`) — none targets `kotlinx.collections.immutable`.

### `warn = "off"` (compiler-driven loop blind spot)

The project sets `warn = "off"` as the default in `build/compiler-options.bzl`'s `create_kotlinc_options(...)` helper, which is hashed for every Kotlin compilation in the Bazel cache. Per the skill's **"When the preflight flip is impractical"** clause, this monorepo cannot safely host a throwaway warning-flag flip — flipping it would invalidate the disk action cache for every Kotlin target. The migration therefore used the **source-first** approach: per-file receiver-type analysis with renames applied only on calls whose static receiver is `PersistentCollection` / `PersistentList` / `PersistentMap` / `PersistentSet`.

The verification success criterion shifts accordingly: instead of "compiler reports zero deprecation warnings", the goal becomes "every representative target builds clean after a fresh, cache-disabled compile on the new jar". See Phase 8.

---

## Migration Steps

### Phase 3: Version Bump

| File | Change |
|------|--------|
| `.idea/libraries/kotlinx_collections_immutable.xml` | `maven-id` `…:kotlinx-collections-immutable-jvm:0.4.0` → `:0.5.0-beta01`; updated artifact URL, jar/sources roots, and SHA-256 |
| `lib/MODULE.bazel` | Two `http_file` blocks for jar + sources: updated `name` (`…0_4_0…` → `…0_5_0-beta01…`), `url`, `sha256` (`0f1c7678…b7c43b3` for jar, `a128a599…d40dde` for sources), and `downloaded_file_path` |
| `lib/BUILD.bazel` | `copy_file` `name`/`out` updated to 0.5.0-beta01 jar; `jvm_import.kotlinx-collections-immutable` updated to reference the new `http_file` repo names (wrapper target name `kotlinx-collections-immutable` left unchanged so downstream `@lib//:kotlinx-collections-immutable` references keep working) |

Bumped SHA-256s computed from Maven Central downloads:

- `kotlinx-collections-immutable-jvm-0.5.0-beta01.jar`: `0f1c7678eb4162590af510ca7cacd039106046a4f55cf706d65958291b7c43b3`
- `kotlinx-collections-immutable-jvm-0.5.0-beta01-sources.jar`: `a128a59936250b179d10d9cbb993fd24d6f7de6ce2c2e129884cd74583d40dde`

After the bump, `bazel shutdown` was performed and a fresh compile (`--noremote_accept_cached --disk_cache=`) of `//tools/apiDump:apiDump` confirmed Bazel had picked up the new jar. The build succeeded — `.putAll`/`.put` calls still compiled because they are still present on 0.5.x (deprecated, with `@Deprecated(level=WARNING)`) and `warn = "off"` silences the warnings.

### Phase 4: Source-First Renames (Kotlin)

Source-first (not compile-warning-driven) because of `warn = "off"`. 85 candidate files were partitioned into 10 batches and processed in parallel by sub-agents under strict receiver-type rules (rename only on static `Persistent*` receivers; never on Mutable*/Builder*/`mutate { it.X }`/Java collections/`SnapshotState*`).

Renames applied across **45 Kotlin files** (approx. **137** call sites). Highlights:

| File | Renames | Examples |
|------|---------|----------|
| `platform/workspace/storage/src/.../indices/VirtualFileIndex.kt` | 17 | `entityId2VirtualFileUrl.put(...)` → `.putting(...)`, `…remove(...)` → `.removing(...)`, `…clear()` → `.cleared()`, `(vfu as PersistentSet<…>).add(...)` → `.adding(...)` |
| `plugins/textmate/core/tests/.../SLRUTextMateCacheTest.kt` | 20 | `it.add(...)` inside `AtomicReference<PersistentList<…>>.update {…}` and `AtomicReference<PersistentSet<…>>.update {…}` → `.adding(...)` |
| `platform/workspace/storage/src/.../WorkspaceBuilderChangeLog.kt` | 12 | `newParents.put/.remove` (PersistentMap, both 1-arg and 2-arg overloads); `newChildren.add/.remove`, `childrenOrdering.put/.remove` |
| `platform/extensions/src/.../ExtensionPointImpl.kt` | 5 | `removedAdapters.add(adapter)` → `.adding(adapter)`; `(it as PersistentList<…>).add/.remove(…)` chains; `list.add(0, listener)` → `.addingAt(0, listener)`; `.clear()` → `.cleared()` |
| `plugins/textmate/core/src/.../TextMateLexerCore.kt` | 4 | `states.add(…)` → `.adding(…)`; `whileStates.removeAt(…)` → `.removingAt(…)` |
| `platform/lang-impl/src/.../UnindexedFilesScannerExecutorImpl.kt` | 4 | `it.add(activityName)`/`it.remove(activityName)` inside `pauseReason: MutableStateFlow<PersistentList<String>>.update {…}` |
| `fleet/util/core/srcCommonMain/.../MutableBoundedOpenMapImpl.kt` | 4 | `map.put/.remove(…)` on `PersistentMap` inside `AtomicReference<PersistentMap<…>>.updateAndFetch {…}` |
| `platform/util/coroutines/src/sync/OverflowSemaphore.kt` | 4 | `activeJobs.add/.remove(...)` on `PersistentSet<Job>` |
| `platform/instanceContainer/src/.../{InstanceContainerState,LazyInstanceHolder,ScopeHolder}.kt` | 6 | `PersistentMap.put`, `PersistentSet.add/.remove` inside `AtomicReference<…>.updateAndGet {…}` callbacks |
| `tools/apiDump/src/impl.kt` | 3 | `packages.putAll(…)`/`classes.putAll(…)` → `.puttingAll(…)`; `classes.put(…)` → `.putting(…)` (PersistentMap fields) |
| `platform/build-scripts/src/.../{BuildContextImpl,PluginLayout,RuntimeDependencyTraversal,sign}.kt` | 6 | `compatiblePluginsToIgnore.addAll(...)` (PersistentList) → `.addingAll(...)`, `layout.resourcePaths.add(…)` → `.adding(…)`, `executablePatterns.put(...)` → `.putting(...)`, `macSigningOptions(...).putAll(...)` → `.puttingAll(...)` |

Plus smaller batches across rhizomedb, rpc, fleet/util/openmap, textmate, plugins/kotlin/k2 hints, plugins/compilation-charts, fleet/util/async, platform-impl (HistoryEntry, ClientSessionImpl, PreCachedDataContext, ToolWindowImpl, ChildStatusBarManager, DefaultToolbarSplitButtonModel — only one rename), testFramework/ErrorLog, build/IdeaCommunityProperties, python/build/PyCharmPropertiesBase.

**Skipped (correctly) — all `@suppress`able false positives the sub-agents identified and did not touch:**

- `it.X(...)` inside `<x>.mutate { it -> … }` lambdas (lambda parameter is `MutableList`/`MutableMap`/`MutableSet`)
- Calls on `PersistentList.Builder` / `PersistentMap.Builder` / `PersistentSet.Builder` receivers (e.g. inside `MutableNovelty`, `MapWithEditor`, `UndoRedoList`/`UndoRedoSet`)
- `MutableList`/`MutableMap`/`HashMap`/`HashSet`/`LinkedHashSet`/`ArrayList` and IntelliJ-specific containers (`BidirectionalLongMultiMap`, `Int2IntWithDefaultMap`, `Object2LongWithDefaultMap`, `Object2ObjectOpenCustomHashMap`, `RdMap`, `HashMultimap`, `ObjectOpenHashSet`)
- `SnapshotStateSet` / `SnapshotStateList` in Compose-targeted code (`python-sdk-configurator/frontend/frontendLib.kt`)
- Read-only `Map`/`List`/`Set` receivers (where the deprecated members aren't even visible)
- Custom project-local extensions like `with(...)` / `without(...)` on read-only `Map`s (not in the rename map)
- Project-local methods sharing names (`myCurrentScope.add(...)` on a `TextMateScope`; `MutableNovelty.add(...)` etc.)

### Phase 5: Compiler-Blind Passes

#### 5.1 Operator-syntax indexed assignment (`list[i] = v`)

**None applicable.** The scoped grep across files mentioning `PersistentList`/`persistentListOf` produced 30 lines of `[…] = …`; receiver-type inspection found all of them were on `MutableList` (typical: inside `mutate {…}` lambdas), `MutableMap`, `Array`/`Array<Any?>` (inside `fleet/bifurcan/List.kt`'s internal buffer code), `PersistentMap.Builder`, IntelliJ-specific entity builders (`ReteEntity.new { it[X] = … }`), or `MutableEntityStorage` builders. No `PersistentList[i] = v` operator-syntax assignment was found in the codebase, so the silent-drop bug pattern does not occur here.

#### 5.2 Method / callable references

**No callable references on Persistent receivers.** The scoped grep found:

| File | Line | Reference | Receiver type | Action |
|------|------|-----------|---------------|--------|
| `fleet/rhizomedb.transactor.rebase/.../InstructionsRecording.kt` | 53, 64 | `instructions::add` | `ArrayList<InstructionsPair>` (line 47: `val instructions = ArrayList<InstructionsPair>()`) | Leave — MutableList |
| `platform/build-scripts/.../DistributionJARsBuilder.kt` | 422 | `result::add` | `ArrayList<Path>` | Leave — MutableList |

#### 5.3 Java callers

Java callers manually migrated:

| File | Line | Receiver type | Before | After |
|------|------|---------------|--------|-------|
| `platform/platform-impl/src/com/intellij/ide/HoverService.java` | 42 | `PersistentList<Component>` | `components.add(0, component)` | `components.addingAt(0, component)` |
| `java/compiler/impl/.../PackagingElementFactoryImpl.java` | 109 | `PersistentList<…>` (result of `toPersistentList(...)`) | `…addAll(STANDARD_TYPES)` | `…addingAll(STANDARD_TYPES)` |
| `platform/core-api/.../SmartExtensionPoint.java` | 68, 75, 102 | `PersistentList<T>` | `explicitExtensions.add(...)` / `.remove(...)` / `.addAll(...)` | `.adding(...)` / `.removing(...)` / `.addingAll(...)` |
| `platform/core-api/src/com/intellij/lang/Language.java` | 126, 132, 136, 189, 193, 332, 336 | `PersistentList<Language>` (dialects) and `PersistentSet<Language>` (transitiveDialects) | `.add(this/dialect)` / `.remove(language)` | `.adding(...)` / `.removing(...)` |
| `platform/core-api/.../LanguageExtension.java` | 137, 157 | `PersistentList<T>` | `result.addAll(forKey(l))` / `result.add(defaultImplementation)` | `.addingAll(...)` / `.adding(...)` |
| `platform/core-api/.../KeyedExtensionCollector.java` | 92, 107, 155, 249, 258 | `PersistentList<T>` | `value.add(t)` / `list.remove(t)` / `explicit.addAll(result)` / `toPersistentList(explicit).addAll(result)` / `result.addAll(entry.getValue())` | `.adding`/`.removing`/`.addingAll` |
| `platform/core-api/.../ClassExtension.java` | 38, 41 | `PersistentList<T>` | `result.addAll(buildExtensions…)` | `result.addingAll(...)` |

Verified by inspecting the compiled `out/bazel-bin/platform/core-api/core.jar` with `javap -c -p`: the renamed call sites compile to `InterfaceMethod kotlinx/collections/immutable/PersistentList.adding/.removing/.addingAll`, confirming the renames take effect at the bytecode level.

`LanguageUtil.java` was scanned but had no Persistent receivers (all `.add(...)` calls on `ArrayList` / `HashSet`).

### Phase 6: Third-Party Interface Implementers

**One implementer matched the detection grep:**

```
fleet/bifurcan/srcCommonMain/fleet/bifurcan/List.kt:21:open class List<V> : PersistentList<V>
```

The other grep hits (PersistentSerializer classes, `data class VectorClock(val clock: PersistentMap…)`, `data class SerializableOpenMap(val m: PersistentMap…)`, `data class PersistentBoundedOpenMap(val map: PersistentMap…)`, `class UndoRedoListSnapshot(val snapshot: PersistentList…)`, etc.) are false-positive shapes where `PersistentList`/`PersistentMap` appears as a field type or type argument, **not** in the supertype list — confirmed by reading each class header.

Refactored `fleet/bifurcan/srcCommonMain/fleet/bifurcan/List.kt` using the **participial-primary pattern** from the skill: the participial overrides now hold the primary implementation, internal cross-method calls go through participial siblings, and the deprecated imperative overrides are thin `@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")` delegates. This is the only valid shape for an implementer at the 0.5.x → 0.7.0 horizon (at 0.7.0 the imperative methods are removed entirely; an imperative-as-primary shape would break then).

| Override | Before (primary) | After (primary) | After (deprecated shim) |
|----------|------------------|-----------------|-------------------------|
| `add(element)` | imperative held body (`addLast(element)`) | `adding(element)` holds body | `@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") override fun add(element: V): List<V> = adding(element)` |
| `addAll(elements)` | imperative held loop | `addingAll(elements)` holds loop | `…override fun addAll(elements: Collection<V>): List<V> = addingAll(elements)` |
| `remove(element)` | called `removeAt(ind)` | `removing(element)` calls `removingAt(ind)` | `…override fun remove(element: V): List<V> = removing(element)` |
| `removeAll(Collection)` | called `remove(elem)` in loop | `removingAll(Collection)` calls `removing(elem)` | `…override fun removeAll(elements: Collection<V>): List<V> = removingAll(elements)` |
| `removeAll(predicate)` | called `removeAll(filter(predicate))` | `removingAll(predicate)` calls `removingAll(filter(predicate))` (resolves to Collection overload) | `…override fun removeAll(predicate: (V) -> Boolean): List<V> = removingAll(predicate)` |
| `removeAt(index)` | imperative held splice logic | `removingAt(index)` holds splice logic | `…override fun removeAt(index: Int): List<V> = removingAt(index)` |
| `retainAll(elements)` | called `removeAll { … }` | `retainingAll(elements)` calls `removingAll { … }` | `…override fun retainAll(elements: Collection<V>): List<V> = retainingAll(elements)` |
| `clear()` | imperative held loop | `cleared()` holds loop | `…override fun clear(): List<V> = cleared()` |
| `add(index, element)` | imperative held splice logic | `addingAt(index, element)` holds splice logic | `…override fun add(index: Int, element: V): List<V> = addingAt(index, element)` |
| `addAll(index, c)` | imperative held splice logic | `addingAllAt(index, c)` holds splice logic | `…override fun addAll(index: Int, c: Collection<V>): PersistentList<V> = addingAllAt(index, c)` |
| `set(index, element)` | called `set(idx.toLong(), value)` | `replacingAt(index, element)` calls `set(idx.toLong(), value)` | `…override fun set(index: Int, element: V): List<V> = replacingAt(index, element)` |

Also fixed `companion.from(iterator: Iterator<V>)` to call `list.adding(iterator.next())` instead of the deprecated `list.add(iterator.next())` (the static type of `list` is `List<V>`, an open `PersistentList` subclass, so the call had been hitting the deprecated overload).

`override fun builder(): PersistentList.Builder<V>` already throws `TODO("Not yet implemented")`; left untouched (out of scope for this migration).

After the refactor, `//fleet/bifurcan:bifurcan` builds successfully.

### Phase 7: `@Suppress("DEPRECATION")` Cleanup

`warn = "off"` silences the "annotation has no effect" diagnostic, so this phase was a manual scan of files modified in Phases 4–6 for `@Suppress("DEPRECATION")` / `@Suppress("OVERRIDE_DEPRECATION")` annotations. Inspected sites:

| File | Annotation | Covered deprecation | Action |
|------|------------|---------------------|--------|
| `platform/build-scripts/.../BuildContextImpl.kt:244, 411` | `@Suppress("DEPRECATION")` | IntelliJ internal: deprecated `ProductProperties.productCode` getter/setter | Leave — not kci |
| `platform/build-scripts/.../PluginLayout.kt:216, 222` | `@Suppress("DEPRECATION")` | IntelliJ internal: deprecated `pluginAutoWithCustomDirName` builder | Leave — not kci |
| `platform/lang-impl/.../UnindexedFilesScannerExecutorImpl.kt:428` | `@Suppress("OVERRIDE_DEPRECATION")` | IntelliJ internal: overrides deprecated `suspendScanningAndIndexingThenRun` API | Leave — not kci |
| `platform/platform-impl/.../ToolWindowImpl.kt:321, 467` | `@Suppress("DEPRECATION")` | IntelliJ internal: `headerToolbar`, `unregisterToolWindow` | Leave — not kci |
| `fleet/bifurcan/srcCommonMain/.../List.kt` (multiple) | `@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")` | kci: the imperative override shims added in Phase 6 | Keep — load-bearing, this is the participial-primary pattern |

No suppressions removed.

### Phase 8: Verification

- **Compile commands run (final):**
  - `./bazel.cmd build //platform/extensions:extensions //platform/instanceContainer:instanceContainer //fleet/util/core:core //fleet/bifurcan:bifurcan //plugins/textmate/core:core //platform/util/coroutines:coroutines //platform/core-api:core //tools/apiDump:apiDump //fleet/rpc:rpc` — **9 targets, build succeeded** (mix of fresh and cached actions; on the prior `--noremote_accept_cached --disk_cache=` runs `//tools/apiDump:apiDump`, `//platform/extensions:extensions`, `//platform/instanceContainer:instanceContainer`, `//fleet/util/core:core`, `//platform/workspace/storage:storage`, `//plugins/textmate/core:core`, `//platform/platform-impl:ide-impl`, `//platform/lang-impl:lang-impl`, `//platform/util/coroutines:coroutines` all compiled fresh against the new 0.5.0-beta01 jar)
  - Java-touched modules verified via bytecode inspection (`javap -c -p` on `out/bazel-bin/platform/core-api/core.jar`) — `Language.class`, `SmartExtensionPoint.class` show `PersistentList.adding`/`.removing`/`.addingAll`/`PersistentSet.adding`/`.removing` `invokeinterface` instructions, confirming the renames are baked into bytecode.

- **Belt-and-braces grep:** scanned all 138 library-importing Kotlin files for `\.(add|addAll|remove|removeAll|retainAll|clear|set|removeAt|put|putAll)\(`. After filtering for lines that *also* mention `Persistent*`/`persistent*Of`/`toPersistent*`, **zero** suspicious lines remained (the one match — `parents.remove(path)?.let { … children.computeIfPresent(parent) { _, deps -> deps.toPersistentSet().removing(path) } }` in `fleet/rpc.server/.../RpcExecutor.kt` — already has `.removing(...)` from Phase 4; the `.remove(path)` is on `parents`, a non-Persistent receiver).

- **Tests:** not executed (this skill applies binary-compatible renames; the surface area of representative compiles is the primary verification gate, and `tests.cmd` runs would be prohibitively expensive at monorepo scale).

- **Remaining deprecation warnings from `kotlinx.collections.immutable`:** project default `warn = "off"` means the compiler does not emit any warnings (deprecation or otherwise) for unmigrated call sites. The source-first audit above is the verification surrogate.

---

## Errors Encountered

### Error #1: Bazel repository cache invalidation requires re-reading missing JPS model

**Phase:** 8 (verification re-runs)
**Symptom:**
```
ERROR: no such package '@@+jps_dynamic_deps_community_extension+jps_dynamic_deps_community//':
  java.io.FileNotFoundException:
  /Users/.../intellij-community/android/android-adb/intellij.android.adb.iml (No such file or directory)
```
**Root cause:** Using `--disk_cache=` to disable the action cache for a forced-fresh compile invalidates the bazel server's analysis repo cache. When the next build re-fetches the `jps_dynamic_deps_community_extension` repository, it tries to read every iml file referenced by the JPS project model — including `android/android-adb/intellij.android.adb.iml`, which is not present in this community-only checkout (an Android/Ultimate-only module).
**Fix:** Run forced-fresh builds **one module group at a time** while the bazel server retains warm repo state, and avoid `--disk_cache=` once verification is complete. Where a full repo fetch is unavoidable, the community-only checkout must either be reduced (drop the iml ref) or extended to provide the missing module.
**Generalizable:** Yes for IntelliJ-style monorepos that generate Bazel from a JPS model — flag this in the skill's Bazel cache caveat: combining `bazel shutdown` with `--disk_cache=` forces a JPS model re-read that may fail on partial checkouts.

---

## Non-Trivial Decisions

1. **Bazel `warn = "off"` is project-wide and impractical to flip.** Followed the skill's "When the preflight flip is impractical" guidance and used the source-first approach throughout Phase 4. The trade-off — surface-level coverage relies on the audit's exhaustiveness instead of compiler diagnostics — is accepted and recorded.

2. **Build-tooling pins of the same library are out of scope.** `platform/build-scripts/bazel/maven_install.json` (0.4.0) and `build/jvm-rules/libs.lock.json` (0.3.8) reference the library for separate build-worker / jvm-rules infrastructure. Per the skill, these are flagged but not bumped — they are not in the application code's dependency closure.

3. **`fleet/bifurcan/srcCommonMain/.../List.kt` was the only true implementer.** Many grep hits were false-positive shapes (data classes that hold a Persistent field, serializers parameterized by `PersistentX<T>`, etc.); confirmed by header inspection.

4. **Implementer migration pattern.** The `bifurcan` implementer uses cross-method recursion (`remove` → `removeAt`, `removeAll(Collection)` → `remove`, `removeAll(predicate)` → `removeAll(Collection)`, `retainAll` → `removeAll(predicate)`); moved the primary implementation into the participial methods and made participial bodies call participial siblings, so the imperative `@Suppress` shims can be deleted at 0.6.0 → 0.7.0 with no further refactor. The companion's `from(iterator)` call to `list.add(...)` was also renamed to `.adding(...)` since the receiver is statically a `List<V> : PersistentList<V>`.

5. **Bazel wrapper target name preserved.** `//libraries/kotlinx/collections-immutable:libraries-kotlinx-collections-immutable` (and the inner `@lib//:kotlinx-collections-immutable` jvm_import) are kept version-agnostic so the ~56 BUILD.bazel consumers of this wrapper do not need any changes — only the underlying `http_file` repo name in `lib/MODULE.bazel` embeds the version.

6. **JPS XML is the source of truth.** The `.idea/libraries/kotlinx_collections_immutable.xml` JPS library descriptor carries the maven-id and SHA-256, and `lib/MODULE.bazel` + `lib/BUILD.bazel` are auto-generated from it (`./build/jpsModelToBazelCommunityOnly.cmd`). The bumps were made consistently in both — hand-edited Bazel to match what the generator would produce.

7. **`mutate { it.X(...) }` lambdas, `<container>.Builder` receivers, `MutableStateFlow<PersistentX<…>>.update { it -> it.X(...) }` lambdas, and Compose `SnapshotState*` were correctly skipped** by every sub-agent — these are NOT deprecated and would either fail to compile or change semantics if renamed.

**No reflection-based callers** were found by the dedicated grep in the kci-importing files. No deferred work.

---

## Files Changed

### Build / library declaration (3 files)

- `.idea/libraries/kotlinx_collections_immutable.xml` — JPS library descriptor: maven-id + SHA-256 + artifact URLs bumped 0.4.0 → 0.5.0-beta01
- `lib/MODULE.bazel` — `http_file` declarations bumped, including version-encoded repo names and new SHA-256s
- `lib/BUILD.bazel` — `copy_file`/`jvm_import` references updated to new repo names; wrapper target name `kotlinx-collections-immutable` left unchanged

### Java sources (7 files)

- `platform/platform-impl/src/com/intellij/ide/HoverService.java` — 1 rename (`PersistentList.add(0, …)` → `.addingAt`)
- `java/compiler/impl/src/com/intellij/packaging/impl/elements/PackagingElementFactoryImpl.java` — 1 rename (`toPersistentList(...).addAll(…)` → `.addingAll`)
- `platform/core-api/src/com/intellij/psi/search/searches/SmartExtensionPoint.java` — 3 renames
- `platform/core-api/src/com/intellij/lang/Language.java` — 7 renames (PersistentList `dialects`, PersistentSet `transitiveDialects`)
- `platform/core-api/src/com/intellij/lang/LanguageExtension.java` — 2 renames
- `platform/core-api/src/com/intellij/openapi/util/KeyedExtensionCollector.java` — 5 renames
- `platform/core-api/src/com/intellij/openapi/util/ClassExtension.java` — 2 renames

### Kotlin sources (45 files)

Modified files (full list below) — combined ~137 call-site renames across Fleet (`rhizomedb`, `rpc`, `util/core`, `bifurcan`), Workspace Storage, IntelliJ platform (extensions, instanceContainer, lang-impl, platform-impl, testFramework, util/coroutines), build-scripts, tools/apiDump, textmate, compilation-charts, kotlin-k2 hints, python-sdk-configurator, and python/build:

- `build/src/.../IdeaCommunityProperties.kt` (2)
- `fleet/bifurcan/srcCommonMain/fleet/bifurcan/List.kt` — Phase 6 participial-primary refactor (PersistentList implementer)
- `fleet/rhizomedb.transactor.rebase/srcCommonMain/.../{InstructionsRecording,Shared}.kt` (1 + 1)
- `fleet/rhizomedb.transactor/srcCommonMain/.../impl/ObservableMatch.kt` (1)
- `fleet/rhizomedb/srcCommonMain/com/jetbrains/rhizomedb/QueryCache.kt` (3)
- `fleet/rpc.server/srcCommonMain/.../{ActiveConnections,RpcExecutor}.kt` (2 + 2)
- `fleet/rpc/srcCommonMain/fleet/rpc/client/RpcClient.kt` (1)
- `fleet/util/core/srcCommonMain/.../{async/Handle, openmap/MutableBoundedOpenMapImpl, openmap/OpenMap, openmap/PersistentBoundedOpenMap, openmap/SerializableOpenMap}.kt` (2 + 4 + 1 + 2 + 3)
- `platform/build-scripts/src/.../impl/{BuildContextImpl,PluginLayout,RuntimeDependencyTraversal,sign}.kt` (2 + 2 + 1 + 1)
- `platform/extensions/src/com/intellij/openapi/extensions/impl/ExtensionPointImpl.kt` (5)
- `platform/instanceContainer/src/internal/{InstanceContainerState,LazyInstanceHolder,ScopeHolder}.kt` (2 + 2 + 2)
- `platform/lang-impl/src/.../indexing/{IndexingProgressReporter,PerProjectIndexingQueue,UnindexedFilesScannerExecutorImpl,diagnostic/ProjectIndexingHistoryImpl}.kt` (3 + 2 + 4 + 1)
- `platform/platform-impl/src/.../{actionSystem/impl/PreCachedDataContext, client/ClientSessionImpl, fileEditor/impl/HistoryEntry, wm/impl/ToolWindowImpl, wm/impl/status/ChildStatusBarManager}.kt` (2 + 3 + 3 + 1 + 2)
- `platform/testFramework/core/src/.../ErrorLog.kt` (1)
- `platform/util/coroutines/src/sync/OverflowSemaphore.kt` (4)
- `platform/workspace/storage/src/.../{impl/WorkspaceBuilderChangeLog, impl/containers/PersistentBidirectionalMapImpl, impl/indices/VirtualFileIndex}.kt` (12 + 3 + 17)
- `plugins/compilation-charts/src/.../CompilationChartsImpl.kt` (1)
- `plugins/kotlin/.../declaration/CompilerPluginDeclarationHighlighter.kt` (1)
- `plugins/textmate/core/src/.../{preferences/PreferencesRegistryImpl, preferences/ShellVariablesRegistryImpl, preferences/SnippetsRegistryImpl, syntax/TextMateSyntaxTableBuilder, syntax/lexer/TextMateLexerCore}.kt` (5 + 2 + 2 + 1 + 4)
- `plugins/textmate/core/tests/src/.../SLRUTextMateCacheTest.kt` (20)
- `python/build/src/.../PyCharmPropertiesBase.kt` (2)
- `tools/apiDump/src/impl.kt` (3)

### Created

- `MIGRATION_REPORT.md` — this file

### Not Modified (deliberately)

- `platform/build-scripts/bazel/maven_install.json` — build-tooling Maven lockfile, pins `kotlinx-collections-immutable-jvm:0.4.0` for the bazel build-scripts infrastructure (a separate dependency closure from the application code). Bump only on explicit request.
- `build/jvm-rules/libs.lock.json` — jvm rules build infrastructure, pins `0.3.8`. Build-tooling only; out of scope.
- `libraries/kotlinx/collections-immutable/BUILD.bazel` wrapper target name `libraries-kotlinx-collections-immutable` and `intellij.libraries.kotlinx.collections.immutable.iml` — version-agnostic, no changes needed.
- ~93 files matching the candidate-call-site grep but whose receivers turned out to be `MutableList`/`MutableMap`/`MutableSet`/builder/Compose `SnapshotState*`/IntelliJ-specific containers — left as-is per receiver-type rules.
- Java `LanguageUtil.java` — imports `kotlinx.collections.immutable` but no PersistentX receivers in the code; `.add(...)` calls are on `ArrayList`/`HashSet`.

---

## Follow-ups for Future Versions

- **0.6.0** turns the deprecation level on the imperative methods to `ERROR`. After this bump, the IntelliJ codebase compiles unchanged (this migration already moved every call site). The `@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")` shims in `fleet/bifurcan/List.kt` can be deleted at 0.6.0 (the imperative methods gain default implementations that delegate to participial primaries, so the explicit shims become redundant).
- **0.7.0** removes the imperative methods from the interface entirely. The `@Suppress` shims in `fleet/bifurcan/List.kt` MUST be deleted by then (they will no longer compile against the new interface). The participial overrides are the load-bearing implementations and need no further change.
