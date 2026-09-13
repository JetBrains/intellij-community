# Compose Desktop Measurement — Choose the Right Tool

**Core principle:** Baseline → Change → Measure Delta. Never commit a performance fix without
numbers showing the before/after improvement.

> **Routing:** Use this workflow before and after any performance optimisation to establish a
> baseline and verify improvement. Also use when the symptom is "it's slow" but no specific
> cause is identified yet.

## Workflow

### Phase 1 — Choose the tool

Match the symptom to the tool:

| Symptom | Primary tool | Secondary tool |
|---------|-------------|----------------|
| High CPU at rest | JFR / YourKit CPU sampling | SideEffect logging |
| Jank during scroll/animation | JFR / YourKit | SideEffect logging per composable |
| Memory growth / OOM | YourKit heap dump / JFR heap | `Runtime.getRuntime()` logging |
| Recompositions without jank | SideEffect logging | Compose tracing runtime |
| Slow startup | JFR startup recording | YourKit CPU sampling |
| Window resize lag | JFR during resize | Subcomposition logging |

For detailed tool setup, read [profiler-setup.md](profiler-setup.md).

### Phase 2 — Establish baseline

- [ ] **1. Define the scenario.** Write down the exact steps:
  - Start the app.
  - Navigate to screen X.
  - Scroll to item Y.
  - Perform action Z.

- [ ] **2. Warm up the JVM.** Run the scenario once to let JIT compile hot paths.

- [ ] **3. Record the baseline.**

For CPU:
```bash
# Pass JVM flags via JAVA_TOOL_OPTIONS — --args are application arguments, not JVM arguments
JAVA_TOOL_OPTIONS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=baseline.jfr" \
  ./gradlew run
```

For logging:
```kotlin
SideEffect { println("[Baseline] ${composableName} recomposed at ${System.currentTimeMillis()}") }
```

For memory:
```kotlin
val runtime = Runtime.getRuntime()
println("[Baseline] used: ${(runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024} MB")
```

- [ ] **4. Quantify the baseline.** Extract one number:
  - CPU: % time in `ComposerKt` or target call stack.
  - Recompositions: count per scenario.
  - Memory: MB retained after GC.
  - Frame time: ms between `withFrameNanos` ticks (approximate).

### Phase 3 — Apply change

- [ ] **5. Make the minimum targeted change.** One fix at a time. Do not combine multiple
  optimisations — you will not know which one worked.

### Phase 4 — Measure delta

- [ ] **6. Re-run the exact same scenario.**

- [ ] **7. Compute delta.**

```
Improvement % = (baseline - after) / baseline * 100
```

- [ ] **8. Decide.**
  - Improvement > 10% and statistically consistent → keep the change.
  - Improvement < 5% or noisy → revert and try a different approach.
  - Regression → revert immediately.

For statistical rigour, read [benchmarking-methodology.md](benchmarking-methodology.md).

### Phase 5 — Document

- [ ] **9. Add a comment.** Explain why the optimisation exists and what the baseline number was.

```kotlin
// Optimisation: hoisted list literal to prevent === failure under Strong Skipping.
// Baseline: 47 recompositions per scroll. After: 3 recompositions per scroll.
private val StaticActions = persistentListOf(Action.Share, Action.Save)
```

## Patterns

### SideEffect recomposition counter

```kotlin
@Composable
fun MeasuredComposable() {
    SideEffect { println("[Recompose] ${this::class.simpleName} at ${System.currentTimeMillis()}") }
    // ...
}
```

### withFrameNanos frame-time approximation

```kotlin
LaunchedEffect(Unit) {
    var lastFrame = 0L
    while (isActive) {
        val now = withFrameNanos { it }
        if (lastFrame != 0L) {
            val deltaMs = (now - lastFrame) / 1_000_000
            if (deltaMs > 17) println("[Frame] dropped: ${deltaMs}ms")
        }
        lastFrame = now
    }
}
```

Gaps > 17ms at 60Hz or > 8ms at 120Hz indicate missed frames.

### Memory snapshot before/after

```kotlin
// Must be called from a background coroutine — never from the main/EDT thread.
suspend fun logMemory(tag: String) = withContext(Dispatchers.IO) {
    System.gc()
    delay(100) // let GC settle
    val runtime = Runtime.getRuntime()
    val used = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    println("[Memory] $tag: ${used}MB")
}
```

Call before and after the scenario from a `LaunchedEffect` or a background coroutine.

## Mandatory rules

- **MUST** establish a reproducible baseline before any performance change.
- **MUST** change one thing at a time.
- **MUST** measure in a warmed-up JVM. Cold-start numbers are misleading.
- **MUST** verify on a release-mode build or equivalent (no debug instrumentation) before
  concluding. Debug builds lie: JIT tiers, assertions, and extra allocations skew numbers.
- **MUST NOT** commit a performance fix without before/after numbers in the PR description or a
  code comment.
- **MUST** prefer JFR for initial broad profiling — it is built into the JDK.
- **MUST** use YourKit when JFR is insufficient (heap analysis, allocation tracking).

## Verification checklist

- [ ] A reproducible scenario is documented (steps, data, duration).
- [ ] Baseline numbers were captured before the change.
- [ ] After numbers were captured using the identical scenario.
- [ ] Delta is calculated and documented.
- [ ] The change is kept only if improvement is >10% and consistent across repeated runs.
- [ ] No regression in unrelated metrics.

## References

- [profiler-setup.md](profiler-setup.md) — JFR, YourKit, async-profiler installation and configuration
- [benchmarking-methodology.md](benchmarking-methodology.md) — sample size, variance, common pitfalls
