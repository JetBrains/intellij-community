# Profiler Setup for Desktop

## JFR (Java Flight Recorder)

Built into JDK 11+. No installation required.

### Command-line recording

```bash
# Pass JVM flags via JAVA_TOOL_OPTIONS — --args are application arguments, not JVM arguments
JAVA_TOOL_OPTIONS="-XX:StartFlightRecording=duration=60s,filename=compose.jfr,settings=profile" \
  ./gradlew run
```

### Analysing the recording

```bash
jfr print --events ExecutionSample compose.jfr | head -n 100
```

Or open in JDK Mission Control (JMC) for a GUI flame graph.

### What to look for

- `ExecutionSample` events with `androidx.compose.runtime.ComposerKt` — recomposition overhead.
- `ExecutionSample` events with `androidx.compose.ui.node.LayoutNode` — layout overhead.
- `AllocationSample` events — object allocation hotspots.

## YourKit

Download from https://www.yourkit.com/download/. Best for heap analysis and live CPU sampling.

### Attach to running process

1. Start the desktop app through the project's normal run configuration or Gradle task.
2. Open YourKit, attach to the Gradle daemon process (or the app process if forked).
3. Start CPU sampling or capture a heap dump.

### Recommended views

- **CPU → Sampling** — look for `ComposerKt`, `SnapshotStateKt`, `LayoutNode`.
- **Memory → Heap Dump** — search for retained `RecomposeScopeImpl`, `LayoutNode` instances.
- **Memory → Live Objects** — watch for growth in `SnapshotStateObserver` or `DerivedSnapshotState`.

## async-profiler

Lightweight, low-overhead profiler. Good for flame graphs without heavy tooling.

```bash
# Download from https://github.com/jvm-profiling-tools/async-profiler
./profiler.sh -d 30 -f flame.html <pid>
```

Open `flame.html` in a browser. Look for wide Compose runtime bars.

## Compose Tracing Runtime

If `androidx.compose.runtime:runtime-tracing` is on the classpath, trace events appear in
profilers:

```kotlin
implementation("androidx.compose.runtime:runtime-tracing:1.0.0")
```

This project does **not** currently include this dependency.

## Quick comparison

| Tool | Install | Best for | Overhead |
|------|---------|----------|----------|
| JFR | None (JDK built-in) | Broad CPU sampling, allocation profiling | Low |
| YourKit | Download + license | Heap dumps, live object search, detailed CPU | Medium |
| async-profiler | Download | Flame graphs, low-overhead production profiling | Very low |
| SideEffect logging | None | Quick recomposition counting | Zero |
