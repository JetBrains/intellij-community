# Desktop Measurement Techniques

Compose Desktop lacks Android Studio's Layout Inspector recomposition counters and the
`@TraceRecomposition` runtime annotation. Use these JVM-appropriate techniques instead.

## 1. SideEffect logging (simplest)

```kotlin
@Composable
fun SuspectRow(data: ItemData) {
    SideEffect { println("[Recompose] SuspectRow id=${data.id} at ${System.currentTimeMillis()}") }
    // ...
}
```

- Add to every suspect composable.
- Reproduce the user journey.
- Count prints. If a composable logs on every scroll tick, animation frame, or idle frame, it is
  a hot path.

## 2. Snapshot recomposition counter (coarse)

```kotlin
val recomposeCounter = mutableIntStateOf(0)

@Composable
fun CountedRecompositions(content: @Composable () -> Unit) {
    SideEffect { recomposeCounter.intValue++ }
    content()
}
```

Display `recomposeCounter` in a debug overlay to see global recomposition frequency.

## 3. JVM profiler (YourKit, JFR, async-profiler)

- Attach YourKit or run with `-XX:+FlightRecorder`.
- Look for `androidx.compose.runtime.*` in CPU flame graphs.
- High time in `ComposerKt$recompose$...` or `SnapshotStateKt` suggests recomposition pressure.

## 4. Compose tracing (when available)

If the project has the Compose Tracing runtime on the classpath, use the trace events:

```kotlin
// Only available if androidx.compose.runtime:runtime-tracing is added
androidx.compose.runtime.trace.Trace.beginSection("MySection")
```

This project does **not** currently depend on `runtime-tracing`.

## What NOT to use on Desktop

| Android tool | Desktop equivalent | Notes |
|-------------|-------------------|-------|
| Layout Inspector recomposition counts | Not available | Use logging instead. |
| `@TraceRecomposition` (skydoves) | Not available | Use `SideEffect { println(...) }`. |
| `MacrobenchmarkRule` / `BaselineProfileRule` | Not available | Desktop has no ART to profile. |
| `FrameTimingMetric` | Not available | Use profiler or logging. |

## Release vs debug builds

Debug builds also run without optimisation. Always verify fixes on a release-ish build
(`./gradlew runRelease` if available). If a composable is slow in debug, measure in release
before concluding — the gap can be significant.
