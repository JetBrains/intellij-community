# Desktop Subcomposition Measurement

## Identifying subcomposition overhead

Subcomposition shows up in profiles as:
- `SubcomposeLayoutSubcompositionState` frames
- `BoxWithConstraints` measure/composition cycles
- `SwingPanel` factory calls during resize

## Profiling techniques

### JFR

```bash
# Pass JVM flags via JAVA_TOOL_OPTIONS — --args are application arguments, not JVM arguments
JAVA_TOOL_OPTIONS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=30s,filename=subcomp.jfr" \
  ./gradlew run
```

Look for:
- `androidx.compose.ui.layout.SubcomposeLayout` in the call stack.
- Repeated `measure` → `subcompose` → `measure` chains.

### YourKit

1. Attach YourKit to the running Desktop app.
2. Capture CPU sampling during window resize.
3. Filter for `SubcomposeLayout` and `BoxWithConstraints`.

### Logging

```kotlin
BoxWithConstraints {
    SideEffect {
        println("[Subcompose] measure: maxWidth=$maxWidth, time=${System.currentTimeMillis()}")
    }
    // ...
}
```

If logs appear on every minor resize event, subcomposition is running too frequently.

## Benchmarking subcomposition cost

1. Create a test composable with and without `BoxWithConstraints`.
2. Resize the window rapidly for 10 seconds.
3. Compare CPU samples.

A well-structured layout without subcomposition should show near-zero Compose overhead during
resize; subcomposition-heavy layouts will show sustained `ComposerKt` and `LayoutNode` activity.

## What to measure

| Metric | Good | Bad |
|--------|------|-----|
| `SubcomposeLayout` CPU % during resize | <5% | >20% |
| `BoxWithConstraints` recomposition count per resize | 1-2 | >10 |
| `SwingPanel` factory calls per recomposition | 1 | >1 |
