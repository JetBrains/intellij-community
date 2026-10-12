# Boundary Violations — IO and Computation in Composition

This document describes the two most critical categories of boundary violation: IO in composition
and inline data work in composition. Both belong in the presenter/state-holder, never in a
composable body.

## Strictly forbidden: IO in composition

The following must **never** appear in a composable body, regardless of how they are wrapped
(even inside `remember { }` — see note below):

**Serialisation / deserialisation**
- JSON (Kotlinx, Gson, Moshi, Jackson/ObjectMapper)
- XML, Protobuf, CSV, or any format parsing/encoding

**Network access**
- HTTP calls (`HttpClient`, `OkHttp`, `Retrofit`, `URL(...)`)
- WebSocket sends, DNS lookups, socket operations

**Database**
- SQLite, Room, Exposed, or any raw JDBC/DriverManager call
- `prepareStatement`, `executeQuery`

**File system**
- `File(...)`, `Files.*`, `FileInputStream`, `BufferedReader`, directory listings
- Any `java.io` or `java.nio` call

**Subprocess / process I/O**
- `ProcessBuilder`, `Runtime.exec()`, RPC stdin/stdout pipes

**Exception:** Image loading delegated to a threading-aware library (Coil, Glide) via their
Compose integration APIs is fine — they manage the threading contract.

### Why `remember { }` does not fix this

`remember { }` still executes on the composition thread on the first composition and whenever
its key changes. A `remember { File(...).readText() }` call blocks the UI thread on every key
change and on every cold start. The correct fix is to move the work to the presenter/state-holder
so it runs off the hot path entirely before the composable is called.

## Inline data work — apply common sense

O(N) complexity is a reliable red flag, but O(1) is not a safe pass. Object allocation, hashing,
synchronisation, or any side-effectful initialisation can be expensive even if nominally constant.

### Always flag (O(N) or worse)

| Category | Examples |
|----------|---------|
| Text scanning | `lineSequence().count()`, `split("\n").size`, `lines().count()` |
| String transforms | `.replace(…)`, `.trimIndent()`, `.format(…)` — allocate proportional to input |
| Collection traversals | `.filter { }`, `.sortedBy { }`, `.groupBy { }`, `.sumOf { }`, `.maxOf { }` |
| Regex inline | `Regex(…)` constructed or executed in composition — compiles pattern every recompose |

### Flag with judgement (nominally O(1) but potentially expensive)

- Factory calls or constructors that do meaningful initialisation work, acquire locks, or touch
  global state.
- `hashCode()` on complex objects.
- `joinToString`, `mapIndexed`, `flatMap`, `associate` on large collections.

### Safe in composition

Simple field reads, comparisons, boolean checks, and property access on already-computed values.
Normal small allocations (data class instances, small literals) are fine.

## Fix pattern

Move the work to the presenter or state-holder:

```kotlin
// WRONG — file read in composition
@Composable
fun LogViewer() {
    val lines = remember { File(logPath).readLines() } // blocks composition thread
    LazyColumn { items(lines) { Text(it) } }
}

// RIGHT — file read in presenter
class LogPresenter {
    val lines: StateFlow<List<String>> = flow {
        emit(File(logPath).readLines())
    }.stateIn(scope, SharingStarted.Lazily, emptyList())
}

@Composable
fun LogViewer(lines: List<String>) {
    LazyColumn { items(lines) { Text(it) } }
}
```
