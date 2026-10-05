# Cache Window and Prefetch

## LazyLayoutCacheWindow

Available on Desktop in Compose Foundation 1.6.0+ (`@OptIn(ExperimentalFoundationApi::class)`).

`LazyLayoutCacheWindow` keeps a buffer of off-screen items composed so they are ready when the
user scrolls them into view. Without it, items are composed on demand as they enter the viewport,
which can cause jank for expensive items.

> **API note:** `LazyLayoutCacheWindow` is an interface. It is passed to `rememberLazyListState`
> — there is no modifier-based API. The API shape is experimental and may evolve between releases;
> verify against the Compose Foundation KDoc for the version in use.

### Dp-based factory (recommended)

```kotlin
@OptIn(ExperimentalFoundationApi::class)
val listState = rememberLazyListState(
    cacheWindow = LazyLayoutCacheWindow(
        ahead = 200.dp,  // compose ~200dp of content ahead of the viewport
        behind = 100.dp, // keep ~100dp of content behind the viewport
    )
)
LazyColumn(state = listState) {
    items(data, key = { it.id }) { item ->
        ExpensiveItem(item)
    }
}
```

### Fraction-based factory

```kotlin
@OptIn(ExperimentalFoundationApi::class)
val listState = rememberLazyListState(
    cacheWindow = LazyLayoutCacheWindow(
        aheadFraction = 0.5f,  // 50% of viewport height ahead
        behindFraction = 0.25f // 25% of viewport height behind
    )
)
```

### Parameters

| Parameter | Meaning | Guidance |
|-----------|---------|----------|
| `ahead` / `aheadFraction` | How far ahead of the viewport to keep items composed | Start with 200.dp or 0.5f. Increase if profiling shows compose-on-entry jank. |
| `behind` / `behindFraction` | How far behind the viewport to retain items | Start with 100.dp or 0.25f. Increase only if back-scroll is also janky. |

### Memory trade-off

Each cached item retains its full composition tree. If an item contains images or complex layouts,
larger cache windows increase memory linearly. Measure heap before and after with YourKit or JFR:

```bash
# With YourKit: capture heap after scrolling through the list
# Compare retained size of LazyListItemNode with and without the cache window
```

### When NOT to use

- List items are trivial (single Text) → cache window adds overhead with no benefit.
- List is short (<20 items) → all items are likely already composed.
- Memory is constrained → prefer smaller values or omit entirely.

## NestedPrefetchScope

`NestedPrefetchScope` allows a parent lazy list to prefetch children of nested lazy lists (e.g.
a `LazyRow` inside a `LazyColumn` item). This is advanced and rarely needed on Desktop.

Use it only when:
- You have a `LazyRow` carousel inside every `LazyColumn` item.
- Profiling shows the carousel items cause jank on first visibility.

```kotlin
@OptIn(ExperimentalFoundationApi::class)
LazyColumn {
    items(data, key = { it.id }) { item ->
        NestedPrefetchScope { // experimental API shape may vary
            CarouselRow(item.images)
        }
    }
}
```

Note: the exact `NestedPrefetchScope` API is still experimental and may change between releases.
Verify against the Compose Foundation 1.10.x KDoc before using.
