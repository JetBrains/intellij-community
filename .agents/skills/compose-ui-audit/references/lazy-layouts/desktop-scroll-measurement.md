# Desktop Scroll Measurement

Compose Desktop lacks Android's `FrameTimingMetric` and `MacrobenchmarkRule`. Use these
JVM-appropriate techniques to measure lazy-list scroll performance.

## 1. SideEffect logging (per-item recompositions)

```kotlin
LazyColumn {
    items(data, key = { it.id }) { item ->
        SideEffect { println("[Scroll] ${item.id} recomposed at ${System.currentTimeMillis()}") }
        ItemRow(item)
    }
}
```

- Scroll slowly. Only newly-visible items should log.
- If already-visible items log repeatedly, you have a recomposition leak.

## 2. CPU sampling during scroll

Attach YourKit, JFR, or async-profiler:

```bash
# JFR — pass JVM flags via JAVA_TOOL_OPTIONS, not --args (--args are application arguments)
JAVA_TOOL_OPTIONS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=scroll.jfr" \
  ./gradlew run
```

Look for:
- `androidx.compose.runtime.ComposerKt` — recomposition overhead.
- `androidx.compose.ui.layout.LayoutNode` — measurement overhead.
- `androidx.compose.foundation.lazy.LazyListState` — scroll state management.

## 3. Heap check for cache window memory

```bash
# With YourKit: capture heap dump after scrolling the full list
# Compare retained size of LazyListItemNode with and without LazyLayoutCacheWindow
```

If `LazyLayoutCacheWindow` is active, expect higher retained count but smoother scroll.
If retained count is excessive, reduce `ahead`/`behind` values.

## 4. Frame-time estimation (approximate)

Desktop Compose does not expose frame timestamps directly. Approximate by logging
`withFrameNanos` in an effect:

```kotlin
LaunchedEffect(Unit) {
    while (isActive) {
        val t = withFrameNanos { it }
        println("frame $t")
    }
}
```

Gaps between frames >16.6ms (60fps) or >8.3ms (120fps) indicate jank.

## What NOT to use

| Android tool | Desktop status |
|-------------|----------------|
| `FrameTimingMetric` | Not available. |
| `MacrobenchmarkRule` | Not available. |
| Layout Inspector recomposition counts | Not available. |
| `@TraceRecomposition` | Not available. |
