# animateItem()

`Modifier.animateItem()` (Compose Foundation 1.7.0+, available on Desktop) animates item
position changes when the data set is modified (insert, remove, reorder).

## Placement

Apply `animateItem()` to the **root modifier of the item content**, not to an inner child:

```kotlin
// RIGHT
LazyColumn {
    items(data, key = { it.id }) { item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateItem(), // on the item root
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.title)
        }
    }
}

// WRONG
LazyColumn {
    items(data, key = { it.id }) { item ->
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = item.title,
                modifier = Modifier.animateItem() // animates only the text, not the row
            )
        }
    }
}
```

## Requirements

- `key` is **mandatory**. Without stable keys, Compose cannot track item identity across
  changes, so there is nothing to animate.
- `contentType` is recommended for mixed layouts so the animation system knows which slots are
  compatible.

## Animation spec

Default is a spring animation. Override the spec:

```kotlin
// Compose 1.7.x / 1.8.x style
Modifier.animateItem(
    fadeInSpec = tween(300),
    fadeOutSpec = tween(300),
    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
)
```

In 1.10.x the exact parameter names may have shifted. Always verify against the current KDoc.

## Common glitches

| Symptom | Cause | Fix |
|---------|-------|-----|
| Items jump instead of sliding | Missing `key` | Add stable `key`. |
| Animation is janky | Item is expensive to measure/layout | Add `LazyLayoutCacheWindow`. |
| State inside animated item is wrong after reorder | `remember` uses stale key | Use `remember(key) { ... }` or hoist state to a presenter. |
| Items overlap during animation | Different `contentType` items in same pool | Add `contentType`. |
