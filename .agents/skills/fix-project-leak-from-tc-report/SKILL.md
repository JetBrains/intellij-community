---
name: fix-project-leak-from-tc-report
description: End-to-end workflow to fix a `_LastInSuiteTest.testProjectLeak` failure reported by TeamCity. Given a TC ashcode / leak diagnostic, this skill drives the full cycle — identify the culprit test, reproduce the leak locally 10× with `tests.cmd`, analyze every unique retention chain observed, apply fixes per chain, and verify by rerunning the leak-hunter until it stops firing. Use when the user asks to fix (not just investigate) a project leak surfaced by TC. Self-contained: does not depend on other skills.
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

Fallback ordering (in decreasing signal strength): heap Instance line → CI ashcode branch → retention-chain class names → the user.

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

**Root cause.** A project-scoped service registered a listener on an application-scoped extension point using `addChangeListener(listener, parentDisposable)`. `ExtensionPointImpl.addChangeListener` adds the adapter to `listeners` **immediately** and only removes it in a `Disposer.register(parentDisposable) { removeExtensionPointListener(adapter) }` cleanup. If that cleanup does not run (or runs after the leak check), the adapter — and everything the listener lambda captures, including the service and its `Project` field — stays alive at the application scope.

**Preferred fix.** Break the strong `this` capture by wrapping in a `WeakReference`. Example (from `CodeStyleSettingsManager.java:199-208`, before/after):

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
