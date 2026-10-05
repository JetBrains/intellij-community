# Keys and contentType

## `key` — identity across recompositions

The `key` parameter in `items()` tells Compose which data item corresponds to which slot. Without
it, Compose uses positional identity: item at index 0 is slot 0, index 1 is slot 1, etc.

### Why positional identity breaks

When you insert at index 0, what was index 0 becomes index 1. Compose sees a different item in
slot 0 and recomposes it. Every item after the insert point recomposes. State inside items
(checkbox, focus, `remember` values) is lost.

### Good keys

| Good | Bad |
|------|-----|
| Database primary key (`id: String` or `id: Long`) | `index` |
| Stable UUID | `hashCode()` (collisions possible) |
| Composite of immutable fields (`"${userId}-${timestamp}"`) | Object reference (`item.toString()`) |

### Key uniqueness

Keys must be unique within the list. Duplicate keys cause undefined behaviour: Compose may
associate the wrong state with the wrong item, or crash in debug builds.

## `contentType` — recycling pool separation

`contentType` tells Compose which items share the same layout structure. Items with the same
`contentType` can reuse each other's slots; items with different `contentType` cannot.

### Without contentType

```kotlin
LazyColumn {
    items(mixed) { item ->
        when (item) {
            is Header -> HeaderUi(item)   // tall
            is Body -> BodyUi(item)       // short
        }
    }
}
```

A tall Header slot is reused for a short Body item. Compose measures and re-lays out the slot,
causing frame drops during fast scroll.

### With contentType

```kotlin
LazyColumn {
    items(mixed, key = { it.id }, contentType = { it::class }) { item ->
        when (item) {
            is Header -> HeaderUi(item)
            is Body -> BodyUi(item)
        }
    }
}
```

Headers reuse header slots, bodies reuse body slots. No cross-type measure thrash.

### When contentType is optional

- All items have the exact same layout → omit `contentType`.
- The list is short (<20 items) and scroll is not critical → omit `contentType`.
- Every item is a different type (e.g. a form with unique fields per row) → `contentType` does
  not help; consider whether a lazy list is the right choice.
