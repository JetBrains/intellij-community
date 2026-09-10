# Nested Lazy Layouts

Compose supports nesting lazy lists (e.g. a `LazyRow` inside a `LazyColumn` item) but each level
adds measurement overhead and scroll coordination complexity.

## Rules

1. **Maximum 2 levels.** `LazyColumn` → `LazyRow` is fine. `LazyColumn` → `LazyRow` →
   `LazyColumn` is not supported well and causes measurement loops.
2. **Each nested list needs its own keys.** The outer list's keys do not protect inner items.
3. **Prefer fixed-size nested lists.** If the nested list is short and bounded, consider
   `Row(horizontalScroll(...))` instead of `LazyRow` to avoid lazy-list overhead.

## Carousel pattern

```kotlin
LazyColumn {
    items(feed, key = { it.id }) { post ->
        Column {
            Text(post.title)
            LazyRow {
                items(post.images, key = { it.url }) { image ->
                    AsyncImage(image.url)
                }
            }
        }
    }
}
```

This is correct but expensive if every post has a carousel. If the carousel has <10 images,
consider:

```kotlin
Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
    post.images.forEach { image ->
        AsyncImage(image.url)
    }
}
```

`Row` + `horizontalScroll` has no lazy overhead and no key requirement for small collections.

## Measurement loops

If a nested lazy list's item height depends on its content, and the parent list's item height
depends on the nested list, Compose may measure in a loop. Symptoms:
- High CPU during scroll.
- `LayoutNode.measure` in profiler flame graphs.
- Scroll position drifting or jumping.

Fix: give the nested list a fixed height (`Modifier.height(200.dp)`) or constrain its item
heights.
