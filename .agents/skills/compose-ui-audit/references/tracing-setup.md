# Tracing Setup Guide

This guide covers how to add AndroidX Tracing 2.0 to a Compose Desktop project from scratch.

---

## Version

Use the latest `2.0.0-alpha06` or later. Check [AndroidX Tracing releases](https://developer.android.com/jetpack/androidx/releases/tracing) for the latest version.

> **Note**: Alpha06 introduced the `Tracer` API with `trace()` and `traceCoroutine()` extension functions.
> Earlier alphas are missing these convenience methods.

---

## Gradle Setup

### 1. Add the dependency

```kotlin
dependencies {
    implementation("androidx.tracing:tracing-wire:2.0.0-alpha06")
}
```

The artifact lives on Google's Maven repository (`google()` in Gradle), not Maven Central — but the `google()` repo is already included in standard Compose Desktop projects.

### 2. Ensure repositories (if needed)

```kotlin
repositories {
    google()  // Required for androidx.tracing
    mavenCentral()
}
```

---

## Tracing Toggle

### Via `./gradlew run` (recommended)

Wire the Gradle property to a JVM system property in `build.gradle.kts`:

```kotlin
// In app/build.gradle.kts
tasks.named<JavaExec>("run") {
    (findProperty("yourapp.tracing") as? String)?.let {
        systemProperty("yourapp.tracing", it)
    }
}
```

Then run:
```bash
./gradlew run -Pyourapp.tracing=true
```

> **Note**: `-Dyourapp.tracing=true` does **not** work with `./gradlew run` — it sets a JVM property on Gradle itself, not the forked app process. Use `-P` (Gradle property) with the forwarding setup above.

### Direct JVM invocation

If you launch the packaged app directly (e.g. from a run configuration's VM args or a shell script), you can pass the system property directly to the JVM:

```bash
java -Dyourapp.tracing=true -jar yourapp.jar
```

---

## Basic Setup

### 1. Create the TracingSession

```kotlin
import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import java.io.File

class TracingSession(private val outputDir: File) {
    private var driver: TraceDriver? = null

    val tracer: Tracer?
        get() = driver?.tracer

    fun start() {
        outputDir.mkdirs()
        val sink = TraceSink(directory = outputDir, sequenceId = 1)
        driver = TraceDriver(sink = sink)
    }

    fun stop() {
        driver?.close()
        driver = null
    }
}
```

### 2. Create the AppTracing object

```kotlin
import androidx.tracing.Tracer
import java.io.File

object AppTracing {
    const val CATEGORY_UI = "ui"
    const val CATEGORY_STATE = "state"
    const val CATEGORY_MARKDOWN = "markdown"
    const val CATEGORY_RPC = "rpc"

    private var session: TracingSession? = null

    val tracer: Tracer?
        get() = session?.tracer

    fun initialize() {
        val enabled = System.getProperty("yourapp.tracing")?.toBooleanStrictOrNull() == true
        if (!enabled) return

        session = TracingSession(File("build/perfetto-traces")).also { it.start() }
    }

    fun shutdown() {
        session?.stop()
        session = null
    }

    inline fun <T> trace(category: String, name: String, crossinline block: () -> T): T {
        val t = tracer ?: return block()
        return t.trace(category = category, name = name, block = block)
    }

    suspend inline fun <T> traceCoroutine(
        category: String,
        name: String,
        crossinline block: suspend () -> T,
    ): T {
        val t = tracer ?: return block()
        return t.traceCoroutine(category = category, name = name) { block() }
    }
}
```

### 3. Initialize at app startup

In your app's main initialization:

```kotlin
fun main() {
    AppTracing.initialize()
    // ... rest of app startup
    // On shutdown:
    AppTracing.shutdown()
}
```

---

## Compose Composition Tracer (Optional but Recommended)

To see recomposition spans in the trace, install a CompositionTracer:

```kotlin
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi

// Inside TracingSession — must be a member function to access private `driver`
@OptIn(InternalComposeTracingApi::class)
fun installCompositionTracer() {
    val d = driver ?: return
    val sectionStack = ThreadLocal<ArrayDeque<AutoCloseable>>()
    val compositionTracer =
        object : CompositionTracer {
            override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
                val closeable = d.tracer.beginSection(
                    category = "RecompositionTracing",
                    name = info,
                    token = null,
                    isRoot = false,
                ) {}
                val stack = sectionStack.get()
                    ?: ArrayDeque<AutoCloseable>().also { sectionStack.set(it) }
                stack.addLast(closeable)
            }

            override fun traceEventEnd() {
                sectionStack.get()?.removeLastOrNull()?.close()
            }

            override fun isTraceInProgress(): Boolean = driver != null
        }
    Composer.setTracer(compositionTracer)
}

// Call from start():
fun start() {
    // ...
    installCompositionTracer()
}
```

---

## Output Location

Traces are written to `build/perfetto-traces/` (or your custom directory). Each app close produces `.perfetto-trace` files.

Open in [ui.perfetto.dev](https://ui.perfetto.dev) for visualization.
