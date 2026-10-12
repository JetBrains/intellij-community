# Tracing API Usage Guide

This guide covers how to use the AndroidX Tracing 2.0 API for adding custom trace sections and metadata.

---

## Basic Trace Sections

### Sync blocks

```kotlin
AppTracing.trace(AppTracing.CATEGORY_STATE, "buildTimeline") {
    // work to trace
}
```

### Suspend blocks (with coroutine context propagation)

```kotlin
AppTracing.traceCoroutine(AppTracing.CATEGORY_MARKDOWN, "processAssistantText") {
    withContext(dispatcher) {
        // async work to trace
    }
}
```

The `traceCoroutine` variant automatically propagates the coroutine context, so the trace continues across suspend/resume boundaries on different threads.

---

## Categories (Semantic Grouping)

Define categories for different subsystems:

```kotlin
object AppTracing {
    const val CATEGORY_UI = "ui"
    const val CATEGORY_STATE = "state"
    const val CATEGORY_MARKDOWN = "markdown"
    const val CATEGORY_RPC = "rpc"
    const val CATEGORY_DB = "db"
    const val CATEGORY_NETWORK = "network"
}
```

Categories appear in Perfetto UI and help filter/find slices.

---

## Adding Metadata (KVPs)

For rich trace data, use `EventMetadata` to add key-value pairs:

```kotlin
val tracer = AppTracing.tracer ?: return buildTimeline()

val metadata = tracer.beginSectionWithMetadata(
    category = "state",
    name = "buildTimeline",
    token = null,  // null = auto-propagate
    isRoot = false,
)

// Add metadata entries
metadata.addMetadataEntry("messageCount", messages.size)
metadata.addMetadataEntry("workspaceRoot", workspaceRoot.toString())
metadata.addMetadataEntry("isStreaming", isStreaming)

// Optional: add correlation ID to link with other traces
// metadata.addCorrelationId(correlationId)

try {
    // work
} finally {
    metadata.dispatchToTraceSink()
    metadata.close()
}
```

### Available metadata types

| Method | Type | Example |
|--------|-----|---------|
| `addMetadataEntry(name, String)` | Text | `"userId": "user_123"` |
| `addMetadataEntry(name, Long)` | Integer | `"messageCount": 42` |
| `addMetadataEntry(name, Double)` | Floating | `"latencyMs": 15.5` |
| `addMetadataEntry(name, Boolean)` | Flag | `"isStreaming": true` |
| `addCorrelationId(Long)` | ID | Link traces across threads |
| `addCorrelationId(String)` | ID | Link traces across threads |

---

## Why Add Metadata?

1. **Debugging**: See actual values (counts, sizes, IDs) directly in the trace without guessing
2. **Filtering**: Find slices by metadata in PerfettoSQL: `WHERE messageCount > 100`
3. **Correlation**: Link related traces (e.g., RPC request → DB query → response)
4. **Debug**: `addCallStackEntry` shows where the trace section was created

Example PerfettoSQL (metadata is in the `args` table, joined via `arg_set_id`):
```sql
SELECT s.name, s.dur / 1000 as duration_ms,
       EXTRACT_ARG(s.arg_set_id, 'messageCount') as message_count
FROM slice s
WHERE s.name = 'buildTimeline'
ORDER BY s.dur DESC
```

---

## Propagation Tokens

Tokens connect trace sections across threads or coroutines:

```kotlin
val token = tracer.tokenFromCoroutineContext()  // Auto from coroutine context

// Or create a custom flow ID:
val flowId = System.nanoTime()  // Unique per trace flow

val section = tracer.beginSection(
    category = "rpc",
    name = "executeTool",
    token = object : PropagationToken {
        override fun contextElementOrNull(): CoroutineContext.Element? = null
    },
    isRoot = false,
)
```

When `token = null`, the tracer automatically uses `tokenFromCoroutineContext()` to propagate across coroutine suspend/resume boundaries.

---

## Best Practices

### 1. Always use the facade

```kotlin
// Good: uses AppTracing which handles null tracer
AppTracing.trace("state", "buildTimeline") { ... }

// Avoid: raw tracer calls without null check
tracer.trace(...)  // Risk: NPE when tracing disabled
```

### 2. Use categories consistently

- `ui` — Compose recomposition, layout, drawing
- `state` — presenter/state holder transformations
- `markdown` — text processing
- `rpc` — subprocess communication
- `db` — database operations (if any)

### 3. Keep span names stable

```kotlin
// Good: stable operation name
AppTracing.trace("state", "buildTimeline") { ... }

// Avoid: including variable data in span name
// This creates one slice per unique message!
AppTracing.trace("state", "processed ${message.id}") { ... }
// Instead use metadata:
AppTracing.trace("state", "processMessage") {
    val metadata = ...
    metadata.addMetadataEntry("messageId", message.id)
}
```

### 4. Don't trace too finely

```kotlin
// Coarse is better:
// Single span for whole operation
AppTracing.trace("markdown", "processMessage") {
    // everything
}

// Fine-grained is harder to read:
AppTracing.trace("markdown", "enrichFileLinks") { ... }
AppTracing.trace("markdown", "parseMarkdown") { ... }
AppTracing.trace("markdown", "buildBlocks") { ... }
```

### 5. Use metadata for variable data

```kotlin
// Good: stable name + metadata via beginSectionWithMetadata
val tracer = AppTracing.tracer ?: return process(message)
val meta = tracer.beginSectionWithMetadata(category = "state", name = "processMessage", token = null, isRoot = false)
meta.addMetadataEntry("messageId", message.id)
meta.addMetadataEntry("blockCount", blocks.size)
try {
    process(message)
} finally {
    meta.dispatchToTraceSink()
    meta.close()
}

// Bad: creates one slice per unique message ID
AppTracing.trace("state", "processMessage:${message.id}") { ... }
```

---

## Complete Example

```kotlin
suspend fun processMessage(message: Message): RenderModel {
    val tracer = AppTracing.tracer
    if (tracer == null) return buildRenderModel(listOf(message))

    val meta = tracer.beginSectionWithMetadata(
        category = AppTracing.CATEGORY_STATE,
        name = "buildRenderModel",
        token = null,  // auto-propagates coroutine context
        isRoot = false,
    )
    meta.addMetadataEntry("workspaceRoot", workspaceRoot.toString())

    return try {
        buildRenderModel(listOf(message)).also {
            meta.addMetadataEntry("messageCount", 1)
        }
    } finally {
        meta.dispatchToTraceSink()
        meta.close()
    }
}
```

---

## Resources

- [AndroidX Tracing API](https://developer.android.com/reference/androidx/tracing/package-summary)
- [Perfetto UI](https://ui.perfetto.dev)
- [PerfettoSQL reference](https://perfetto.dev/docs/analysis/trace-processor)
