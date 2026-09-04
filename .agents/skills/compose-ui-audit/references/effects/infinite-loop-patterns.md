# Infinite Loop Patterns

Infinite recomposition loops are the most severe effect misuse. They freeze the UI and peg CPU.

## Pattern 1: Effect key changes on every write

```kotlin
// BUG — LaunchedEffect key = count; count++ changes key → effect restarts → count++ again
var count by remember { mutableIntStateOf(0) }
LaunchedEffect(count) {
    count++ // count changes → recomposition → new key → LaunchedEffect restarts
}
// Note: LaunchedEffect(Unit) would NOT loop — it runs exactly once regardless of recomposition.
```

**Fix:** Use a stable key and add suspension to avoid tight-loop state writes:

```kotlin
// FIXED — Unit key runs once; delay prevents tight-loop recomposition
LaunchedEffect(Unit) {
    while (isActive) {
        delay(1000)
        count++
    }
}
```

## Pattern 2: derivedStateOf writes to state it reads

```kotlin
// BUG — derivedStateOf is read-only; writing inside it is undefined behaviour
val filtered by remember {
    derivedStateOf {
        if (items.value.isEmpty()) {
            showEmptyState.value = true // DO NOT DO THIS
        }
        items.value.filter { it.active }
    }
}
```

**Fix:** Move the side effect out of `derivedStateOf`:

```kotlin
val filtered by remember { derivedStateOf { items.value.filter { it.active } } }

LaunchedEffect(filtered) {
    showEmptyState.value = filtered.isEmpty()
}
```

## Pattern 3: snapshotFlow collects into state that feeds the flow

```kotlin
// BUG — scrollPosition drives itself
val scrollPosition = mutableIntStateOf(0)
LaunchedEffect(Unit) {
    snapshotFlow { scrollPosition.intValue }
        .collect { scrollPosition.intValue = it + 1 }
}
```

**Fix:** Do not write to the same state you are observing. Use a different state target or a
`Flow` operator.

## Pattern 4: Unstable key causes restart storm

```kotlin
// BUG — lambda is a new object every recomposition
LaunchedEffect(onRefresh) { onRefresh() }

// FIX — stabilise the key or use rememberUpdatedState
val currentOnRefresh by rememberUpdatedState(onRefresh)
LaunchedEffect(Unit) { currentOnRefresh() }
```

## Detection

- UI freezes or becomes unresponsive.
- CPU usage spikes to 100% of one core.
- Logs from the effect print continuously.
- Compose debug builds may throw an exception after too many recompositions.

## Fix strategy

1. Add `delay(1)` or `withFrameNanos` inside the effect to break the synchronous cycle.
2. Identify which state write triggers the recomposition.
3. Move the write to a different coroutine, a different phase, or a different composable.
4. Ensure effect keys are stable primitives or use `rememberUpdatedState`.
