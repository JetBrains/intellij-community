---
name: fix-project-leak-from-tc-report
description: Fix TeamCity project-leak test failures from diagnosis through repeated verification.
---

# Fix a `_LastInSuiteTest.testProjectLeak` from a TeamCity report

This is the full **fix** workflow. Investigation-only tasks (identify + reproduce + analyze) are covered by the sibling `leaking-test-investigation-by-tc-report` skill; the two overlap in phases 1–3 but this one adds phases 4–5 (apply fix, verify).

The workflow is:

1. **Identify** the culprit test from the TC report.
2. **Reproduce** the leak locally 10 times, archiving every heap dump + stdout.
3. **Analyze** all unique retention chains observed (local ± CI-reported).
4. **Fix** each retention chain independently with a pattern from the catalogue below.
5. **Verify** by rerunning the leak-hunter loop on the patched tree and confirming it stops firing.

The bundled scripts are self-contained — do not source the other skill's copies.

## Phase 1 — identify the culprit test

The leaked `Project` name is the strongest signal. In `ProjectRule()` / heavy-project rules the project name defaults to the test class simple name. So:

- Look for `Instance: Project(name=<TestClassSimpleName>, containerState=DISPOSE_COMPLETED, …)` in the TC report.
- Locate the file with ijproxy `search_symbol` / `search_file`; only ask the user if the class name matches multiple files and the retention chain does not disambiguate.
- Record the test module (`.iml` next to the test's `testSrc/`) — this is the `--module` for `tests.cmd`.

If the project name is generic (`light_temp_…`), pick the first non-platform class from the retention chain (`ProjectCodeStyleSettingsManager`, a plugin service, a Mockito mock, …) and search callers/tests referencing it plus the plugin area.

Fallback ordering (in decreasing signal strength): heap Instance line → CI hashcode branch → retention-chain class names → the user.

## Phase 2 — reproduce locally 10 times

`_LastInSuiteTest.testProjectLeak` is filtered out of pattern discovery (`community/platform/testFramework/core/src/com/intellij/TestCaseLoader.java:181-182,382-384`); its body runs via `JUnit5TestSessionListener.testPlanExecutionFinished`
(`community/platform/testFramework/src/com/intellij/tests/JUnit5TestSessionListener.java:39-193`), gated on `UsefulTestCase.IS_UNDER_TEAMCITY == true`, i.e. env `TEAMCITY_VERSION != null`.

To activate locally without pretending to be a real agent, set `TEAMCITY_VERSION` **and** point `TEAMCITY_BUILD_PROPERTIES_FILE` at a two-line stub properties file (needed so `TeamCityHelper.getPersistentCachePath` in the wrapper doesn't crash):

```bash
mkdir -p /tmp/tc-stub-cache /tmp/tc-stub-tmp
cat > /tmp/tc-stub.properties <<'EOF'
agent.persistent.cache=/tmp/tc-stub-cache
teamcity.build.tempDir=/tmp/tc-stub-tmp
EOF
```

Then run the bundled loop script — do **not** hand-roll 10 sequential `tests.cmd` calls in the harness:

```bash
"${CLAUDE_SKILL_DIR}/scripts/leak-loop.sh" \
  --module <test-module-iml-name> \
  --test  <test-FQN> \
  --runs  10 \
  --archive /tmp/<name>-leak-runs
```

`leak-loop.sh` sets the two env vars for you (auto-creating the stub properties file if missing), loops N times, copies each run's `Heap dump is published to <path>` file to `$ARCHIVE/run-NN.hprof.zip`, saves stdout to `run-NN.output`, and prints one boundary line per run so a `Monitor` can subscribe.

**Fast-exit contract.** If run 1 produces no heap dump (no leak line in the output), the script aborts with **exit code 3** and does not queue the remaining runs. Treat this signal explicitly:

| Exit code | Meaning                                     | Interpretation during Phase 2                    |
|-----------|---------------------------------------------|--------------------------------------------------|
| `0`       | All N runs finished; all N produced a leak. | Deterministic leak. Proceed to analysis.         |
| `3`       | Run 1 produced no heap dump; loop aborted.  | Either the leak is not reproducible locally, or the leak-hunter didn't fire. Debug before continuing. |

If exit code is 3 in Phase 2, verify that the run's stdout contains an `ideaTests.totalTimeMs` line (comes from `JUnit5TestSessionListener.testStatistics` after the leak check) — if yes, `testProjectLeak` fired and found no leak (unreliable repro; check the test suite membership, ordering, and any co-located classes). If no, the listener didn't fire — recheck `TEAMCITY_VERSION` propagation.

Cold Bazel compile + JBR download makes run 1 take 4–5 minutes; subsequent runs are ~30–60 s each with warm caches.

## Phase 3 — analyze every unique retention chain

After Phase 2, invoke:

```bash
"${CLAUDE_SKILL_DIR}/scripts/summarize-runs.sh" /tmp/<name>-leak-runs
```

It writes `SUMMARY.md` with a per-run table plus the set of unique retention-chain shapes (class-only, argument values stripped). This tells you whether all runs leaked the same way or you have multiple parallel bugs.

Then hand-write `<archive-dir>/ANALYSIS.md` — **required before writing any fix** — covering:

1. **What reproduced.** Test FQN + module, N/N run ratio, list of unique retention chains.
2. **Each unique retention chain**, both local *and* the one reported by CI (if different). Print the full `via '<field>'` path with class names, and label the chain (e.g. Chain L / Chain C).
3. **Root cause per chain.** Locate the retaining code via `search_symbol` and cite `file:line-range`.
4. **Recommended fix per chain** — pick from the catalogue below.

**Do not skip the CI-reported chain even if you only reproduced a different one locally.** The leak checker reports one path per project; multiple retainers can coexist on the same leaked instance. Fixing only one usually promotes the next to be reported next time.

## Phase 4 — apply fixes

### MANDATORY first check — is the retaining class a Disposable project service?

**Before you apply any pattern from this catalogue, answer this question about the class that ends up as a Disposer ROOT (or otherwise pins the `Project`):**

Is the class both

1. registered via `<projectService>` in a plugin descriptor or annotated `@Service(Level.PROJECT)` (equivalently `@Service` with a project-level scope inferred by the container), **and**
2. a `Disposable` (implements `Disposable` directly or via a base class)?

If **YES**, then the service container itself already registers this instance into the Disposer tree and disposes it correctly. Specifically:

- `community/platform/service-container/src/com/intellij/serviceContainer/ServiceInstanceInitializer.kt:61-62` — on construction, the container calls `Disposer.register(componentManager.serviceParentDisposable, instance)`. The service is parented under `serviceParentDisposable`, which is itself a child of the project (or app) in the Disposer tree. **The service is not supposed to be a Disposer ROOT.**
- `community/platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:904-905` — on disposal, the container calls `Disposer.dispose(instance)` (not `instance.dispose()`), which cascades to every child registered under the service, invokes `instance.dispose()`, and removes the instance's entry from `ObjectTree.myObject2ParentNode`.

Consequences for the fix:

- `Disposer.register(this, child)` inside such a service's constructor (typical uses: `new Alarm(SWING_THREAD, this)`, `messageBus.connect(this)`, `EP.addExtensionPointListener(project, listener, this)`) is the **canonical, correct pattern**. Do **not** change `this` → `project` (or any other `parentDisposable`) at those call sites. That reduces Disposer tree height but does not fix any leak — see "Anti-pattern — flattening the Disposer tree by hoisting children to `project`" below.
- If the retention chain still terminates at `ObjectTree.myObject2ParentNode` with the service as a Disposer ROOT, then something on the framework path above did **not** happen for this specific case. The actual bug lives there. Investigate, in this order:
  - **Constructor-vs-container-registration ordering / re-registration semantics.** The service's constructor runs `Disposer.register(this, alarm)` before the container runs `Disposer.register(serviceParentDisposable, this)`. If the second call is a no-op / rejected because `this` is already present in `ObjectTree` with an implicit ROOT parent, the service stays a ROOT even after the container "registers" it. Read `ObjectTree.register` / `Disposer.register` for the reparenting rules.
  - **Light-project reset path.** `LightPlatformTestCase.tearDown` and `TestApplicationManager` release reuse projects across test classes and have custom reset semantics. If they don't route through the container's `Disposer.dispose(instance)` cascade, the `ObjectTree` entry lingers. Recent tracking commits are under `IJPL-247543` — start there with `git log --grep IJPL-247543 -- '**/serviceContainer/**' '**/LightPlatform**' '**/LeakHunter**'`.
  - **The leak-hunter's timing relative to `Disposer.dispose(project)` cascades.** A race window can report a ROOT that would have been cleaned up milliseconds later.
  - **Recent framework changes.** Grep the same paths under `IJPL-247543` for context.

If **NO** (the class is not a Disposable project service, or it is manually instantiated / registered outside the standard service container path), fall through to the "Root cause first" section and pick a pattern.

### Root cause first — fix disposal, not the reference

A `Project` reaches the leak-hunter because *some* object holds it. That object falls into one of three categories, and the correct fix depends on which:

- **(A) Legitimately outlives the `Project` by design** — an application-scope cache, message bus, extension point, thread-local, static field. Then the *retention itself* is the bug: break it (unregister the listener, clear the cache on `projectClosed`, add a `parentDisposable`), or — only when unregistration is genuinely impossible — weaken the reference at the retention boundary.
- **(B1) Should NOT outlive the `Project` AND is a properly-registered `Disposable` project service** — registered via `<projectService>` / `@Service(Level.PROJECT)`, implements `Disposable`. The framework path (`ServiceInstanceInitializer.kt:61-62` + `ComponentManagerImpl.kt:904-905`) is expected to dispose the instance and remove it from `ObjectTree`. If the retention chain shows the service still a Disposer ROOT, **something on the framework path did not happen for this case** — investigate the possibilities listed in the "MANDATORY first check" above. Do **not** paper over it by moving children out from under `this`.
- **(B2) Should NOT outlive the `Project` AND is NOT integrated with the service container** — a manually-instantiated helper, a project-scoped listener registered elsewhere, a project-scoped `CoroutineScope`, or anything registered under the `Project`'s Disposer subtree by test / production code that bypasses the container. Here the retention is a **symptom of a structural disposal bug** and a real fix is warranted: register it properly (`Disposer.register(project, this)` in the constructor, migrate to `@Service(Level.PROJECT)`, route disposal via `Disposer.dispose(...)` at the call site, add a missing `parentDisposable`, unregister the listener, evict from the cache on `projectClosed`).

**(B1) is by far the more common case for a service that appears as a Disposer ROOT, and neither `WeakReference` on `myProject` nor flattening the child-Disposer registrations is a valid fix for it.** Wrapping a service's own `myProject` field in `WeakReference` only makes the `Project` GC-able while leaving the underlying disposal bug in place — see the anti-pattern below. Moving children out from `this` to `project` reduces Disposer tree height without fixing why the service itself lingers in `ObjectTree` — see the other anti-pattern below.

Only reach for `WeakReference` after you have identified case (A) and confirmed the container legitimately outlives its contents.

### Anti-pattern — `WeakReference` on a service's own `Project` field

**Do not** "fix" a project-service leak by wrapping the service's own `myProject` field in a `WeakReference`. Example of what NOT to do:

```java
// BAD — hides the disposal bug, does not fix it.
private final WeakReference<Project> myProjectRef;
public MyService(Project project) { myProjectRef = new WeakReference<>(project); }
private Project project() { return Objects.requireNonNull(myProjectRef.get(), "disposed"); }
```

Why this is wrong:

- A project service **must be disposed together with the project**. If the retention chain shows a project service pinning its own `Project`, the service is outliving the project — that is the actual bug. The most common concrete culprits (for cases outside category (B1) — see the MANDATORY first check for the (B1) framework path first):
  - A service registered a listener on an application-scope extension point / message bus without a `parentDisposable` tied to the project.
  - A service registered itself into a static / application-scope cache and no `projectClosed` handler evicts it.
  - A non-standard registration path bypassed `ServiceInstanceInitializer` so the container never routed the service through `Disposer.dispose(instance)`.
- Weakening the field only masks the `Project` reference. The service instance itself is still leaked (still reachable from wherever the disposal bug is). Message-bus subscriptions still fire on the "dead" service; `Disposable` children of the service stay in the tree; log lines and telemetry keep referencing the disposed project by name.
- `Objects.requireNonNull(myProjectRef.get(), ...)` starts throwing NPE from unexpected paths (`isDisposed()` checks, teardown callbacks, log formatters) as soon as GC clears the reference — usually intermittently and only under load.
- Every future GC pause, heap-shape change, or class-loader tweak can silently re-expose the leak by making the retention checker report the SERVICE (still leaked) or a downstream field instead of the `Project`. You have not fixed the leak; you have moved the leak checker's crosshair. TC will keep reporting a leak; the report just points somewhere else.

**Correct approach for a service-in-the-Disposer-tree chain — diagnosis flow:**

1. **Verify container integration.** Confirm the retaining class is registered via `<projectService>` in a plugin descriptor or annotated `@Service(Level.PROJECT)`, and implements `Disposable`. If YES, the framework path from `community/platform/service-container/src/com/intellij/serviceContainer/ServiceInstanceInitializer.kt:61-62` (registration under `serviceParentDisposable`) plus `community/platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:904-905` (`Disposer.dispose(instance)` on shutdown) **should** dispose the service and remove its `ObjectTree` entry. If the chain still shows the service as a Disposer ROOT, **the bug is on the framework path or in the disposal ordering, not in the service's constructor**. Investigate why cleanup didn't happen for this specific case (see the "MANDATORY first check" possibilities: constructor-vs-container ordering / re-registration semantics, light-project reset path, non-standard registration, leak-hunter timing, recent `IJPL-247543` commits). In this case `Disposer.register(this, child)` in the constructor is canonical and must **not** be changed.
2. **Only if the service is NOT properly integrated with the container** (manually instantiated by test / production code, custom registration path that bypasses `ServiceInstanceInitializer`, or a non-service Disposable that ended up as a Disposer ROOT), apply a structural registration fix. Options, in order:
   - Migrate the class to `@Service(Level.PROJECT)` / `<projectService>` so the container owns its Disposer wiring.
   - Register the instance into the project's Disposer subtree explicitly at the construction site with `Disposer.register(project, instance)`.
   - Route the disposal call through `Disposer.dispose(service)` where the caller currently invokes `service.dispose()` directly (that leaves the `ObjectTree` entry behind).
   - Add a missing `parentDisposable` at the listener / EP registration site.
3. **Never as the first move: flattening children into `project`.** Rewriting `Disposer.register(this, child)` to `Disposer.register(project, child)` inside a Disposable service's constructor only reduces Disposer tree height — it does not remove the service from `ObjectTree`. See "Anti-pattern — flattening the Disposer tree by hoisting children to `project`" below.
4. Only after these are exhausted and you have confirmed with a repro loop that the service genuinely cannot be disposed in time (an infrastructure bug that is out of scope for the ticket), consider surface-level containment — and prefer a **test-side dispose in `tearDown`** or an explicit unregistration hook over field-weakening.

### Anti-pattern — flattening the Disposer tree by hoisting children to `project`

**Do not** "fix" a project-service leak by rewriting `Disposer.register(this, child)` in the service's constructor to `Disposer.register(project, child)` (or to any other higher-level `parentDisposable`). Example of what NOT to do:

```java
// BAD — reduces Disposer tree height but does not fix the leak.
public DbPsiFacadeImpl(@NotNull Project project) {
  Disposer.register(project, new Alarm(SWING_THREAD, this)); // was: Disposer.register(this, ...)
  Disposer.register(project, project.getMessageBus().connect()); // was: connect(this)
  EP.addExtensionPointListener(project, listener, project); // was: parentDisposable = this
}
```

Symptom this often gets confused with: the retention chain terminates at `ObjectTree.myObject2ParentNode` with the service instance itself as a Disposer ROOT (e.g. `ObjectNode.myObject → MyServiceImpl@...`, `ObjectTree.myObject2ParentNode → ...`, `(root) → ObjectTree`, `Disposable chain to Disposer ROOT: MyServiceImpl@... <- ROOT`).

Why it's tempting: removing the `Disposer.register(this, ...)` calls in the constructor appears to "remove the ROOT node" (children no longer keep the service pinned via their parent-node backref) and a single local run of the leak-hunter can look clean.

Why it's wrong:

- For a properly-registered `Disposable` project service (`<projectService>` / `@Service(Level.PROJECT)`), children under `this` are **already disposed via the container's `Disposer.dispose(instance)` cascade** — see `community/platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:904-905`. `Disposer.register(this, alarm)` is the canonical way to tie the child's lifetime to the service's lifetime and it is not the source of the leak.
- Moving those children to `project` bypasses the service's own disposal cascade and gives the children the *project's* lifetime instead of the *service's* lifetime. If the service is later replaced (dynamic plugin unload, `replaceServiceInstance` in a test, or any hot-reload path), the old service's children linger under `project` until project close — a fresh leak / behavioural bug you introduced.
- It does **not** fix the retention chain in CI. The container is still responsible for cleaning up the service's own `ObjectTree` entry (see `ServiceInstanceInitializer.kt:61-62` and `ComponentManagerImpl.kt:904-905`); whatever prevented that from happening still prevents it after your edit. The next TC run will report the same service or the next-most-downstream retainer.

Correct alternative: treat the retention as a bug on the framework's disposal path, not in the service's constructor. Investigate `ObjectTree.register` / `Disposer.register` reparenting semantics, the light-project reset path (`LightPlatformTestCase.tearDown`, `TestApplicationManager`), non-standard registration, and the leak-hunter's timing (see the "MANDATORY first check" and `IJPL-247543`). Preserve `Disposer.register(this, child)` in the service constructor.

### Chain families

Two chain families cover the vast majority of `testProjectLeak` failures. Match your observed chain to one of the patterns:

### Pattern A — Mockito thread-local retention on the EDT

Signature: chain terminates in `MockingProgressImpl.ongoingStubbing` → `Thread[AWT-EventQueue-…].threadLocals` → `IdeEventQueue` (root). The retained argument array contains the leaked `Project`.

**Root cause.** Every mock invocation goes through Mockito's `MockHandlerImpl.handle(...)`, which writes the current `Invocation` into a `MockingProgress` ThreadLocal and never clears it. Tests annotated `@RunsInEdt` run on the EDT, so this ThreadLocal accumulates on the EDT and outlives the test class.

**Preferred fix.** Replace `mock<Foo>()` + `whenever(...)` with a small hand-rolled fake. Example (from `ApkEditorTest.kt`, before/after):

```kotlin
// BEFORE
private fun mockFileEditorProviderManager(): FileEditorProviderManager {
  val mgr = mock<FileEditorProviderManager>()
  val provider = mock<FileEditorProvider>()
  whenever(provider.accept(any(), any())).thenReturn(true)
  whenever(provider.createEditor(any(), any())).thenAnswer { TestFileEditor(it.arguments[1] as VirtualFile) }
  whenever(mgr.getProviderList(any(), any())).thenReturn(listOf(provider))
  return mgr
}

// AFTER
private class FakeFileEditorProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean = true
  override fun createEditor(project: Project, file: VirtualFile): FileEditor = TestFileEditor(file)
  override fun getEditorTypeId(): String = "test-fake"
  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.NONE
}
private class FakeFileEditorProviderManager : FileEditorProviderManager {
  private val providers = listOf(FakeFileEditorProvider())
  override fun getProviderList(project: Project, file: VirtualFile) = providers
  override suspend fun getProvidersAsync(project: Project, file: VirtualFile) = providers
  override suspend fun getDumbUnawareProviders(project: Project, file: VirtualFile, excludeIds: Set<String>) =
    providers.filterNot { excludeIds.contains(it.editorTypeId) }
  override fun getProvider(editorTypeId: String) = providers.firstOrNull { it.editorTypeId == editorTypeId }
}
```

**Fallback fix** (when a fake is too much work — many abstract methods, awkward suspend signatures, etc.): hoist the mocks to class fields and reset them on the EDT after each test.

```kotlin
private val mgr = mock<FileEditorProviderManager>()
private val provider = mock<FileEditorProvider>()

@After
fun clearMockitoEdtState() {
  runInEdtAndWait {
    Mockito.reset(mgr, provider)          // clears MockingProgress ThreadLocal on this thread
  }
}
```

The `Mockito.reset(...)` route resolves the ThreadLocal on whichever thread it's called on, so the `runInEdtAndWait { }` wrapper is load-bearing.

### Pattern B — Extension-point / message-bus listener with a strong `this` capture

Signature: chain contains `ExtensionPointImpl.listeners` (or `MessageBusImpl.subscribers`) → `<listenerAdapter>.handle` → a lambda / anonymous inner class whose `arg$1` is the retaining service, whose field points at the leaked `Project`.

This pattern applies at a **retention boundary** where an application-scope container (`ExtensionPointImpl.listeners`, `MessageBusImpl.subscribers`) legitimately outlives a project-scope service by design. That is a category (A) case — the app-scope container is *supposed* to live longer than any single project. `WeakReference` here weakens the reference *at the boundary between scopes*, on the lambda's captured `this`. It does not weaken the service's own `myProject` field (see anti-pattern above).

If your chain does not go through `ExtensionPointImpl.listeners` / `MessageBusImpl.subscribers` and instead terminates in `ObjectTree.myObject2ParentNode` or a project-scope holder, this is not Pattern B — do not apply this fix. Go back to the "Root cause first" section.

**Root cause.** A project-scoped service registered a listener on an application-scoped extension point using `addChangeListener(listener, parentDisposable)`. `ExtensionPointImpl.addChangeListener` adds the adapter to `listeners` **immediately** and only removes it in a `Disposer.register(parentDisposable) { removeExtensionPointListener(adapter) }` cleanup. If that cleanup does not run (or runs after the leak check), the adapter — and everything the listener lambda captures, including the service and its `Project` field — stays alive at the application scope.

**Preferred fix.** First, verify that removing / unregistering the listener at project dispose is genuinely not feasible (usually because the `parentDisposable` disposal path is not under your control). Only then, break the strong `this` capture by wrapping in a `WeakReference` at the lambda boundary. Example (from `CodeStyleSettingsManager.java:199-208`, before/after):

```java
// BEFORE
protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
  FileIndentOptionsProvider.EP_NAME.addChangeListener(this::notifyCodeStyleSettingsChanged, disposable);
  ...
}

// AFTER
protected void registerExtensionPointListeners(@Nullable Disposable disposable) {
  // WeakReference indirection: the app-scoped extension point outlives project-scoped subclasses,
  // so a missed disposal here would pin the manager (and its Project) via the listener list.
  java.lang.ref.WeakReference<CodeStyleSettingsManager> selfRef = new java.lang.ref.WeakReference<>(this);
  FileIndentOptionsProvider.EP_NAME.addChangeListener(() -> {
    CodeStyleSettingsManager self = selfRef.get();
    if (self != null) self.notifyCodeStyleSettingsChanged();
  }, disposable);
  ...
}
```

Why this is safe: while the service container holds a strong reference to the manager, `selfRef.get()` returns non-null and the listener behaves identically. After container release (project dispose for a project service), the WeakReference can clear and the extension point's listener list no longer pins the manager. Same trick applies to anonymous-inner-class listeners — pull the enclosing `this` into a `WeakReference` local and use `ref.get()` inside every callback method.

**Do not** simply remove `addChangeListener`. The listener has a real behavioural purpose; you must preserve that when the service is alive.

### Other patterns (rarer)

- **Static/application cache holding a project reference.** Retention chain terminates in a static field or an application service's Map/List. Fix: use `WeakHashMap` / `ContainerUtil.createWeakMap` keyed by project, or explicitly clear on `ProjectManagerListener.projectClosed`.
- **Background coroutine/executor holding a captured project.** Retention chain terminates in a `CoroutineScope.job` node or a `Thread.target` runnable field. Fix: scope the coroutine to `project.coroutineScope` (auto-cancelled on project close) instead of `GlobalScope` / `Dispatchers.Default`.
- **`MessageBus` subscription without a disposable.** Retention chain contains `MessageBusImpl.subscribers`. Fix: use the `subscribe(Topic, listener, parentDisposable)` overload, not the two-arg one.

## Phase 5 — verify

Rerun the same loop, targeting the same archive directory (or a fresh one). The loop's fast-exit is the desired positive signal here:

```bash
"${CLAUDE_SKILL_DIR}/scripts/leak-loop.sh" \
  --module <test-module-iml-name> \
  --test  <test-FQN> \
  --runs  3 \
  --archive /tmp/<name>-leak-verify
```

**Interpretation of the verify run**:

| Signal                                                                                          | Meaning                                        |
|-------------------------------------------------------------------------------------------------|------------------------------------------------|
| Exit code `3` + first-run `ideaTests.totalTimeMs` line present + no `Found a leaked instance`. | **Success**: `testProjectLeak` ran and passed. |
| Exit code `0` + N/N runs still producing hprofs.                                                | Fix did not close the leak. Reopen `ANALYSIS.md` and look for a chain you missed (or a wrong pattern).   |
| Exit code `3` + no `ideaTests.totalTimeMs` in the stdout.                                       | The leak-hunter didn't fire. Recheck `TEAMCITY_VERSION` propagation and the properties stub.             |

For extra confidence do 2–3 additional plain `tests.cmd` invocations after the loop's success, greping the output:

```bash
export TEAMCITY_VERSION=local-leak-check TEAMCITY_BUILD_PROPERTIES_FILE=/tmp/tc-stub.properties
for i in 1 2 3; do
  ./tests.cmd --module <mod> --test <FQN> > /tmp/verify-$i.out 2>&1
  leak=$(./community/tools/rg.cmd -c 'Found a leaked instance' /tmp/verify-$i.out || echo 0)
  stats=$(./community/tools/rg.cmd -c 'ideaTests.totalTimeMs'   /tmp/verify-$i.out || echo 0)
  echo "run $i: leaked=$leak listener_ran=$stats"
done
```

Expected: every run prints `leaked=0 listener_ran=1`.

## Reporting

Finish with a compact table like:

|                                        | Before fix                | After fix          |
|----------------------------------------|---------------------------|--------------------|
| `Found a leaked instance` occurrences  | N/N runs                  | 0/M runs           |
| `Heap dump is published` occurrences   | N/N runs                  | 0/M runs           |
| Listener ran (`ideaTests.totalTimeMs`) | N/N                       | M/M                |
| Test JVM exit code                     | 41 (leak)                 | 0 (clean)          |

Attach the diff of the fixes (test-side + platform-side) and the path to the archived `SUMMARY.md` / `ANALYSIS.md`. This is the deliverable — enough for a PR reviewer to reproduce, understand, and sign off.

## Guardrails

- **`WeakReference` is not the default fix.** The default fix for a project-scope retention is to *fix disposal* — remove a misplaced `Disposer.register(this, child)` **only when the enclosing class is NOT a properly-registered Disposable project service**, add a missing `parentDisposable`, unregister the listener, evict from the cache on `projectClosed`. `WeakReference` only applies at a genuine cross-scope boundary (Pattern B). Wrapping a service's own `myProject` in `WeakReference` is an anti-pattern that hides the disposal bug rather than fixing it (see Phase 4 "Anti-pattern"). If you find yourself reaching for `WeakReference<Project>` on a service field, stop and re-read the "Root cause first" preamble.
- **For a Disposable project service, `Disposer.register(this, child)` is canonical — do NOT remove it.** The service container itself parents Disposable services under `serviceParentDisposable` (`community/platform/service-container/src/com/intellij/serviceContainer/ServiceInstanceInitializer.kt:61-62`) and disposes them via `Disposer.dispose(instance)` (`community/platform/service-container/src/com/intellij/serviceContainer/ComponentManagerImpl.kt:904-905`), which cascades to all children registered under `this`. Rewriting `Disposer.register(this, child)` → `Disposer.register(project, child)` in a Disposable service's constructor only reduces Disposer tree height and does NOT fix any leak (see Phase 4 "Anti-pattern — flattening the Disposer tree by hoisting children to `project`"). If a Disposable project service still appears as a Disposer ROOT in the retention chain, the bug is on the framework path — investigate the "MANDATORY first check" possibilities before touching the constructor.
- **Never edit `_LastInSuiteTest`, `JUnit5TestSessionListener`, or `TestCaseLoader` to work around a leak** — those are the leak checker itself. If the check is spuriously failing, that is a testFramework bug, not a per-test fix.
- **Do not silence the leak checker locally** (e.g. `-Dintellij.build.test.ignoreFirstAndLastTests=true`) as a "fix" — that only hides it.
- **Do not shorten a WeakReference wrapper into a soft/phantom reference.** Soft references defer collection under memory pressure and will not close the leak deterministically. Phantom references cannot be `get()`-ed.
- **Do not commit changes to `/tmp/tc-stub*.properties`** — those are throwaway files, not repo state.

## Key source references

- Culprit-test filter for `_LastInSuiteTest`: `community/platform/testFramework/core/src/com/intellij/TestCaseLoader.java:181-182,382-384`.
- Suite hook that fires `testProjectLeak`: `community/platform/testFramework/src/com/intellij/tests/JUnit5TestSessionListener.java:39-193`.
- TC-gate flag: `community/platform/testFramework/src/com/intellij/testFramework/UsefulTestCase.java:143`.
- Leak checker: `TestApplicationManager.testProjectLeak()` — search `TestApplicationManager.kt` under `community/platform/testFramework/src/com/intellij/testFramework/`.
- Extension-point listener disposal contract: `community/platform/extensions/src/com/intellij/openapi/extensions/impl/ExtensionPointImpl.kt:730-735`.
- `tests.cmd` semantics: `.claude/skills/testing/SKILL.md`.
