# Benchmarking Methodology

## Sample size

Run the scenario **at least 5 times** and discard the first run (JVM warm-up). Use the median,
not the mean, to reduce outlier impact.

| Metric | Minimum runs | Discard first? |
|--------|-------------|----------------|
| Recomposition count | 5 | Yes |
| CPU time % | 5 | Yes |
| Memory retained | 3 | Yes (force GC before each) |
| Frame time | 10 | Yes |

## Variance and significance

If the standard deviation is >20% of the mean, the scenario is not reproducible. Fix the
scenario (same data, same steps, same duration) before comparing numbers.

A change is "significant" if:
- The median after is >10% better than the median before.
- The after range (median ± stddev) does not overlap the before range.

## Common pitfalls

### Cold-start bias

The first run after JVM start is always slower. Always discard it.

### Debug build bias

Debug builds disable optimisations and enable Live Literals. They can be 2-5× slower than
release. Always verify the final number on a release build.

### GC noise

Memory measurements vary with GC timing. Force GC before each measurement:

```kotlin
// Call from a background coroutine, not from the main/EDT thread
suspend fun forceGc() = withContext(Dispatchers.IO) {
    System.gc()
    delay(100) // let GC settle
}
```

### Observer effect

Attaching a profiler (especially YourKit with allocation recording) slows the app. Use
low-overhead tools (JFR, async-profiler) for the final verification.

### Combining changes

Never A/B test multiple optimisations at once. If A + B together show 15% improvement but A
alone shows 12% and B alone shows -3%, you would incorrectly keep B.

## Documentation template

```markdown
## Performance fix: <brief description>

### Scenario
1. <step 1>
2. <step 2>

### Baseline (median of 5 runs)
- Metric: <value>

### After (median of 5 runs)
- Metric: <value>

### Delta
- <percentage>% improvement

### Tool
- <JFR / YourKit / logging>
```
