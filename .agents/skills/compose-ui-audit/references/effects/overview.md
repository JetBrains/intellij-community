# Compose Effects — Correctness and Loop Prevention

**Core principle:** Use the smallest effect that fits.

- One-shot async work → `LaunchedEffect`.
- Publish to non-Compose on every composition → `SideEffect`.
- Setup/teardown with a resource → `DisposableEffect`.
- Derive state without cascading recompositions → `derivedStateOf`.
- Convert Snapshot state to Flow → `snapshotFlow`.

Never use an effect when a plain `remember` or direct state read suffices.

> **Routing:** Use this workflow when an audit finding points to effects running too often,
> failing to clean up, causing infinite recomposition loops, or questionable `derivedStateOf` use.

## Workflow

### Phase 1 — Measure

- [ ] **1. Add logging to the effect.**

```kotlin
LaunchedEffect(userId) {
    println("[Effect] LaunchedEffect started for $userId")
    val data = repository.fetch(userId)
    state.value = data
}
```

If the log prints repeatedly without `userId` changing, the effect is restarting too often.

- [ ] **2. Check for infinite loops.** If the UI freezes or CPU spikes, look for effects that
  write state whose change triggers recomposition and restarts the effect.

- [ ] **3. Check cleanup.** For `DisposableEffect`, verify `onDispose` runs:

```kotlin
DisposableEffect(listener) {
    listener.start()
    println("[Effect] started")
    onDispose {
        listener.stop()
        println("[Effect] disposed")
    }
}
```

Navigate away. If "disposed" never logs, the composition is retained unexpectedly.

### Phase 2 — Diagnose

- [ ] **4. Match the effect to the use case.**

| Effect | Use for | Misuse |
|--------|---------|--------|
| `LaunchedEffect(key)` | One-shot async work when `key` changes. | Restarting on every callback change because callback is not stabilised. |
| `SideEffect` | Run side code on every successful composition. | Running async work. |
| `DisposableEffect(key)` | Setup/teardown tied to `key` lifetime. | Forgetting `onDispose` or capturing a stale callback. |
| `rememberUpdatedState` | Wrap a callback so `LaunchedEffect`/`DisposableEffect` does not restart when callback changes. | Using it when the callback genuinely should restart the effect. |
| `derivedStateOf` | Derive a value from upstream state without recomposing when derived value hasn't changed. | Using it for transforms that never filter. |
| `snapshotFlow` | Convert Snapshot state to a cold Flow. | Using it when direct state observation suffices. |

For the full effect selection guide, read [effect-selection-guide.md](effect-selection-guide.md).

- [ ] **5. Check effect keys.** Keys decide when an effect restarts.
  - Unstable object as key → restarts on every recomposition.
  - No key when one is needed → runs once and never updates.
  - `true` or `Unit` for "run once" — correct, but document why.

- [ ] **6. Check for infinite-loop signatures.**

```kotlin
// INFINITE LOOP — key changes on every write, so the effect restarts on every recomposition
var count by remember { mutableIntStateOf(0) }
LaunchedEffect(count) {      // key = count
    count++                  // writes count → recomposition → new key → LaunchedEffect restarts
}
// (LaunchedEffect(Unit) would NOT loop — it runs exactly once regardless of recomposition)
```

For loop patterns and fixes, read [infinite-loop-patterns.md](infinite-loop-patterns.md).

- [ ] **7. Check `derivedStateOf` necessity.**

```kotlin
// WRONG — derivedStateOf adds overhead for a trivial transform
val isEmpty by remember { derivedStateOf { items.isEmpty() } }

// RIGHT — direct read is cheaper
val isEmpty = items.isEmpty()
```

```kotlin
// RIGHT — derivedStateOf prevents recompositions when filter result is unchanged
val visibleItems by remember { derivedStateOf { items.filter { it.isVisible } } }
```

For the full `derivedStateOf` guide, read [derived-state-of-guide.md](derived-state-of-guide.md).

### Phase 3 — Fix

- [ ] **8. Stabilise effect keys.** Use primitives, stable ids, or `rememberUpdatedState`.

```kotlin
// WRONG — lambda is unstable, restarts on every recomposition
LaunchedEffect(onRefresh) { onRefresh() }

// RIGHT — method reference or rememberUpdatedState
val currentOnRefresh by rememberUpdatedState(onRefresh)
LaunchedEffect(Unit) { currentOnRefresh() }
```

- [ ] **9. Add missing `onDispose` body.**

```kotlin
// WRONG — leaks the channel: cleanup is forgotten
DisposableEffect(channel) {
    channel.open()
    onDispose { /* forgot to call channel.close() */ }
}

// RIGHT — cleans up
DisposableEffect(channel) {
    channel.open()
    onDispose { channel.close() }
}
```

- [ ] **10. Break infinite loops.** Move state writes outside the composition cycle or use
  `snapshotFlow` + `collect` with a termination condition.

- [ ] **11. Replace unnecessary `derivedStateOf`.**

### Phase 4 — Verify

- [ ] **12. Re-run logging.** Effects should start only when their keys change.
- [ ] **13. Verify cleanup.** Navigate away and check that resources are released.
- [ ] **14. Verify no infinite loops.** UI is responsive; CPU is idle.
- [ ] **15. Verify `derivedStateOf` filters correctly.** Upstream changes that don't affect the
  derived value should not cause recompositions.

## Patterns

### `rememberUpdatedState` for callbacks

```kotlin
@Composable
fun Timer(onTick: () -> Unit) {
    val currentOnTick by rememberUpdatedState(onTick)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            currentOnTick() // always calls the latest callback
        }
    }
}
```

### `DisposableEffect` for lifecycle-bound resources

```kotlin
@Composable
fun ChatChannel(channelId: String) {
    DisposableEffect(channelId) {
        val channel = ChatClient.open(channelId)
        onDispose { channel.close() }
    }
}
```

### `derivedStateOf` for filtered lists

```kotlin
val visibleTodos by remember {
    derivedStateOf { todos.filter { !it.isCompleted } }
}
```

Adding a completed todo changes `todos`, but `visibleTodos` stays the same → no recompositions.

### `snapshotFlow` for Flow interop

```kotlin
LaunchedEffect(Unit) {
    snapshotFlow { scrollState.value }
        .filter { it > 100 }
        .collect { showScrollToTop = true }
}
```

## Mandatory rules

- **MUST** use the smallest effect that fits.
- **MUST** stabilise `LaunchedEffect`/`DisposableEffect` keys.
- **MUST** provide a non-empty `onDispose` in every `DisposableEffect`. Missing cleanup is a memory leak.
- **MUST NOT** write to Snapshot state in a way that triggers immediate recomposition of the same
  composable without a suspension point.
- **MUST** use `rememberUpdatedState` when an effect must observe a frequently-changing callback
  without restarting.
- **MUST NOT** use `derivedStateOf` for trivial transformations that never filter.
- **MUST** use `derivedStateOf` when a derived value filters or collapses upstream changes.
- **MUST** verify `DisposableEffect` cleanup runs by testing composition leave.

## Verification checklist

- [ ] Effects start only when their keys change (logging confirms).
- [ ] `DisposableEffect` resources are cleaned up when the composable leaves composition.
- [ ] No infinite recomposition loops — UI is responsive, CPU is idle at rest.
- [ ] `derivedStateOf` is used only when the derived value filters upstream changes.
- [ ] `rememberUpdatedState` is used for callbacks inside long-lived effects.
- [ ] `SideEffect` is used only for publishing to non-Compose systems, not for async work.

## References

- [effect-selection-guide.md](effect-selection-guide.md) — cheat-sheet: which effect for which use case
- [infinite-loop-patterns.md](infinite-loop-patterns.md) — common infinite-loop signatures and fixes
- [derived-state-of-guide.md](derived-state-of-guide.md) — when to use `derivedStateOf`, when to skip it
