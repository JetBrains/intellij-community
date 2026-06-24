# Compose Lazy Layouts — Scroll Performance and Correctness

**Core principle:** Measure → Optimise → Measure. If scrolling is smooth, the list is fast
enough. Focus only on visible jank, state loss, or flicker.

> **Routing:** Use this workflow when an audit finding points to lazy-list scroll jank, items
> losing state on scroll, flicker on insert/remove, or unexpected recompositions during scroll.

## Workflow

### Phase 1 — Measure

- [ ] **1. Reproduce the user journey.** Scroll at normal speed, then fast-fling. Observe: jank?
  flicker? state loss?

- [ ] **2. Add temporary logging to item composables.**

```kotlin
LazyColumn {
    items(data, key = { it.id }) { item ->
        SideEffect { println("[Lazy] item ${item.id} recomposed") }
        ItemRow(item)
    }
}
```

If every item logs on every scroll tick, you have a recomposition problem. If only newly-visible
items log, behaviour is correct.

- [ ] **3. Profile scroll CPU.** Attach YourKit or run with JFR. Look for high time in
  `ComposerKt` or `SnapshotStateKt` during scroll.

For Desktop-specific measurement, read
[desktop-scroll-measurement.md](desktop-scroll-measurement.md).

### Phase 2 — Diagnose

- [ ] **4. Check for missing `key`.** Without `key`, Compose uses positional identity. On
  insert/remove/reorder, every item after the change point is treated as new.

- [ ] **5. Check for missing `contentType`.** Without `contentType`, items with different layouts
  cannot reuse each other's slots, raising bind/unbind overhead.

- [ ] **6. Check for capturing lambdas inside `items { }`.** `items { }` is a `LazyListScope`
  extension, not a `@Composable` function. Strong Skipping does **not** auto-remember lambdas here.

- [ ] **7. Check for nested lazy layouts.** Each nested list needs its own keys and should not be
  more than 2 levels deep.

- [ ] **8. Check scroll state retention.** If the list resets to top on tab switches or
  navigation, `LazyListState` is not being kept at the right scope.

For key/contentType deep-dive, read [keys-and-content-type.md](keys-and-content-type.md).

### Phase 3 — Fix

- [ ] **9. Add stable, unique `key`.** The key must be stable across recompositions and unique
  within the list. `id` fields are ideal; `hashCode()` or `index` are not.

```kotlin
LazyColumn {
    items(data, key = { it.id }) { item -> ItemRow(item) }
}
```

- [ ] **10. Add `contentType` when items have different layouts.**

```kotlin
LazyColumn {
    items(items = data, key = { it.id }, contentType = { it::class }) { item ->
        when (item) {
            is Header -> HeaderItem(item)
            is Body -> BodyItem(item)
        }
    }
}
```

- [ ] **11. Stabilise lambda parameters.** Use method references or hoist lambdas outside
  `items`.

```kotlin
// WRONG — new lambda every recompose
items(data, key = { it.id }) { item -> ItemRow(item, onClick = { vm.select(item.id) }) }

// RIGHT — method reference is stable
items(data, key = { it.id }) { item -> ItemRow(item, onClick = vm::select) }
```

- [ ] **12. Use `LazyLayoutCacheWindow` for expensive items.** Pass it through
  `rememberLazyListState` — there is no modifier API for the cache window.

```kotlin
@OptIn(ExperimentalFoundationApi::class)
val listState = rememberLazyListState(
    cacheWindow = LazyLayoutCacheWindow(ahead = 200.dp, behind = 100.dp)
)
LazyColumn(state = listState) { /* ... */ }
```

`LazyLayoutCacheWindow` is an interface; factory takes `Dp` distances. A fraction-based variant
also exists: `LazyLayoutCacheWindow(aheadFraction = 0.5f, behindFraction = 0.25f)`. The API is
experimental and may change; verify against the current KDoc.

For cache window and prefetch details, read [cache-window-and-prefetch.md](cache-window-and-prefetch.md).

- [ ] **13. Use `Modifier.animateItem()` for insert/remove animations.**

```kotlin
@OptIn(ExperimentalFoundationApi::class)
LazyColumn {
    items(data, key = { it.id }) { item ->
        ItemRow(modifier = Modifier.animateItem(), item = item)
    }
}
```

For animation patterns and pitfalls, read [animate-item.md](animate-item.md).

- [ ] **14. Handle nested lazy layouts carefully.**

For nested layout rules, read [nested-lazy-layouts.md](nested-lazy-layouts.md).

### Phase 4 — Verify

- [ ] **15. Re-run the logging test.** Only newly-visible items should log during scroll.
- [ ] **16. Re-run the profiler.** CPU during scroll should drop.
- [ ] **17. Test state retention.** Scroll to item 50, scroll away, back — item 50 retains state.
- [ ] **18. Test insert/remove/reorder.** Items should not flicker or jump.

## Patterns

### Keys prevent state loss

```kotlin
// WRONG — checkboxes reset when scrolled out and back
LazyColumn { items(todos) { todo -> Checkbox(checked = todo.done, ...) } }

// RIGHT — state survives scroll
LazyColumn { items(todos, key = { it.id }) { todo -> Checkbox(checked = todo.done, ...) } }
```

### contentType improves recycling

```kotlin
// WRONG — Header and Body share slots, causing measure thrash
LazyColumn { items(mixed) { item -> when (item) { is Header -> ...; is Body -> ... } } }

// RIGHT — separate pools, no thrash
LazyColumn {
    items(mixed, key = { it.id }, contentType = { it::class }) { item ->
        when (item) { is Header -> HeaderUi(item); is Body -> BodyUi(item) }
    }
}
```

### Hoist LazyListState to survive navigation

```kotlin
// WRONG — resets on every navigation
@Composable fun ChatScreen() {
    val listState = rememberLazyListState()
    LazyColumn(state = listState) { /* ... */ }
}

// RIGHT — hoist into the presenter or a long-lived state holder and pass it down
@Composable fun ChatScreen(listState: LazyListState) {
    LazyColumn(state = listState) { /* ... */ }
}
// In the presenter or coordinator: val listState = LazyListState()
```

## Mandatory rules

- **MUST** provide `key` for every `items`/`itemsIndexed` call where the data set can change.
  Exception: truly static lists that never mutate.
- **MUST** provide `contentType` when a list contains items with different layout structures.
- **MUST NOT** use `index` as a `key` — on insert/remove every index shifts and every item is new.
- **MUST NOT** use `hashCode()` as a key unless guaranteed unique and stable.
- **MUST** stabilise lambdas passed into `items { }` blocks.
- **MUST NOT** deeply nest lazy layouts (>2 levels).
- **MUST** measure before adding `LazyLayoutCacheWindow`. It increases memory.
- **MUST** use `Modifier.animateItem()` on the **item root**, not on an inner child.

## Verification checklist

- [ ] Every `items`/`itemsIndexed` call has a `key` (or is explicitly static).
- [ ] Lists with mixed layouts have `contentType`.
- [ ] No capturing lambdas inside `items { }` blocks.
- [ ] Logging confirms only newly-visible items recompose during scroll.
- [ ] State (focus, selection, scroll position) survives scroll-out-and-back.
- [ ] Insert/remove/reorder does not cause flicker or jump.
- [ ] If `LazyLayoutCacheWindow` is used, memory was measured before and after.
- [ ] If `animateItem()` is used, animations run smoothly.

## References

- [keys-and-content-type.md](keys-and-content-type.md) — key selection, contentType pools
- [cache-window-and-prefetch.md](cache-window-and-prefetch.md) — `LazyLayoutCacheWindow`, memory trade-offs
- [animate-item.md](animate-item.md) — `Modifier.animateItem()` placement, glitch avoidance
- [nested-lazy-layouts.md](nested-lazy-layouts.md) — nesting limits, carousel patterns
- [desktop-scroll-measurement.md](desktop-scroll-measurement.md) — logging, CPU profiling on Desktop
