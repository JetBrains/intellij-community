# Phase Deferral

Compose has three main phases. Reading state in an earlier phase than necessary causes
recomposition; deferring the read to a later phase avoids it.

## The three phases

1. The **Composition** phase decides what UI exists by running `@Composable` functions. Reading state during
   composition triggers recomposition.
2. The **Layout** phase decides where UI is placed by running `measure` and `place`. Reading state during layout
   triggers relayout without recomposition.
3. The **Draw** phase decides how UI is rendered by running `draw`. Reading state during draw triggers a redraw without
   recomposition or relayout.

## Phase cost

| Phase | Cost |
|-------|------|
| Recomposition (Composition) | Highest — re-runs composable functions, reallocates objects. |
| Relayout (Layout) | Medium — re-measures and re-places affected nodes. |
| Redraw (Draw) | Lowest — re-executes draw commands on the GPU. |

## Modifier phase map

| Modifier | Phase | State read causes |
|----------|-------|-------------------|
| `Modifier.size()` | Composition | Recomposition |
| `Modifier.offset(x.dp, y.dp)` | Composition | Recomposition |
| `Modifier.padding()` | Composition | Recomposition |
| `Modifier.offset { IntOffset(...) }` | Layout | Relayout |
| `Modifier.layout { ... }` | Layout | Relayout |
| `Modifier.graphicsLayer { ... }` | Draw | Redraw |
| `Modifier.drawBehind { ... }` | Draw | Redraw |
| `Modifier.drawWithContent { ... }` | Draw | Redraw |

## Deferral examples

### Offset

```kotlin
// Composition phase — recomposes every frame
Modifier.offset(x = scrollX.value.dp)

// Layout phase — relayouts, no recomposition
Modifier.offset { IntOffset(scrollX.value.roundToInt(), 0) }

// Draw phase — redraws, no recomposition or relayout
Modifier.graphicsLayer { translationX = scrollX.value }
```

### Size

```kotlin
// Composition phase — recomposes
Modifier.height(height.value.dp)

// Layout phase — relayouts
Modifier.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(maxHeight = height.value.roundToInt())
    )
    layout(placeable.width, placeable.height) {
        placeable.placeRelative(0, 0)
    }
}
```

### Alpha / rotation

```kotlin
// Composition phase — recomposes
Modifier.alpha(alpha.value)

// Draw phase — redraws
Modifier.graphicsLayer { this.alpha = alpha.value }
```

## Rule of thumb

- If the state changes on every animation frame, defer the read to **draw** (`graphicsLayer`).
- If the state changes on scroll or resize, defer the read to **layout** (`offset { }`, `layout { }`).
- If the state changes on user interaction (click, toggle), reading in **composition** is fine.
