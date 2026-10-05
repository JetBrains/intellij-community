# derivedStateOf Guide

`derivedStateOf` creates a new `State` that derives from one or more upstream states. Its purpose
is to prevent recompositions when the upstream changes but the derived value does not.

## When to use

Use `derivedStateOf` when:
- The derivation **filters** or **collapses** upstream changes.
- Downstream composables should not recompose on every upstream tick.

## When NOT to use

Do not use `derivedStateOf` when:
- The derivation is trivial and never filters (e.g. `items.size`).
- The derived value always changes when upstream changes.
- You only read the value once and do not observe it.

## Examples

### RIGHT — filters upstream changes

```kotlin
val isScrolled by remember {
    derivedStateOf { scrollState.value > 0 }
}
```

`scrollState.value` changes on every scroll pixel. `isScrolled` only changes once — when the
threshold is crossed. Consumers of `isScrolled` recompose once instead of on every scroll event.

### RIGHT — collapses a collection

```kotlin
val hasErrors by remember {
    derivedStateOf { items.any { it.isError } }
}
```

`items` may change frequently, but `hasErrors` only changes when an error appears or disappears.

### WRONG — trivial transformation

```kotlin
// Overhead with no benefit
val count by remember { derivedStateOf { items.size } }
```

`items.size` changes whenever `items` changes. Just read `items.size` directly.

### WRONG — always changes

```kotlin
// Overhead with no benefit
val doubled by remember { derivedStateOf { counter.value * 2 } }
```

`doubled` changes on every `counter` change. Just read `counter.value * 2` directly.

## Performance note

`derivedStateOf` adds a small overhead (extra state object, extra observer). The benefit is
negative if the derived value changes as often as the upstream. Use it only when the derivation
reduces recomposition frequency.

## Common mistake: writing inside derivedStateOf

```kotlin
// WRONG — side effects inside derivedStateOf
val filtered by remember {
    derivedStateOf {
        if (items.isEmpty()) showEmpty.value = true
        items.filter { it.visible }
    }
}
```

`derivedStateOf` must be pure. Move side effects to `LaunchedEffect` or `SideEffect`.
