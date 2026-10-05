# Effect Selection Guide

## Quick reference

| Situation | Use | Don't use |
|-----------|-----|-----------|
| Fire-and-forget async work | `LaunchedEffect(key)` | `SideEffect` |
| Run code on every successful composition | `SideEffect` | `LaunchedEffect` |
| Setup/teardown a resource | `DisposableEffect(key)` | `LaunchedEffect` + manual cleanup |
| Callback inside a long-lived effect | `rememberUpdatedState` | Restarting effect on callback change |
| Derive state without cascading recompositions | `derivedStateOf` | Direct transformation |
| Convert Snapshot state to Flow | `snapshotFlow` | Manual polling |

## LaunchedEffect

```kotlin
LaunchedEffect(userId) {
    val data = repository.fetch(userId)
    uiState.value = UiState.Success(data)
}
```

- Runs in a coroutine scoped to the composition.
- Cancels and restarts when `key` changes.
- `key` can be `Unit` for "run once" semantics.
- **Do not** write to state in a tight loop without suspension — this causes CPU starvation.

## SideEffect

```kotlin
SideEffect {
    analytics.trackScreenView("Settings")
}
```

- Runs after every successful composition (not on every recomposition attempt).
- For publishing to non-Compose systems: analytics, logging, imperative view updates.
- **Do not** launch coroutines here — use `LaunchedEffect`.

## DisposableEffect

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event -> /* ... */ }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

- `onDispose` runs when the composable leaves composition or the key changes.
- **Always** provide a non-empty `onDispose`. Missing cleanup is a memory leak.

## rememberUpdatedState

```kotlin
val currentOnTimeout by rememberUpdatedState(onTimeout)
LaunchedEffect(Unit) {
    delay(5000)
    currentOnTimeout() // calls the latest callback, not the one at launch
}
```

- Wraps a value so it can be read without triggering recomposition or effect restart.
- Use when an effect must observe a prop that changes frequently.

## derivedStateOf

```kotlin
val isScrolled by remember {
    derivedStateOf { scrollState.value > 0 }
}
```

- Creates a new state that derives from upstream state.
- Downstream composables only recompose when the derived value changes, not when upstream
  changes.
- Use when the derivation filters or collapses changes.

## snapshotFlow

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { searchQuery.value }
        .debounce(300)
        .flatMapLatest { query -> search(query) }
        .collect { results -> state.value = results }
}
```

- Reads Snapshot state and emits values as a Flow.
- Use for complex reactive pipelines (debounce, switchMap, etc.).
