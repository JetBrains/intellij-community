# Compose Modifiers — Phase, Order, and State Reads

**Core principle:** Measure → Optimise → Measure. A modifier that reads `State<T>` during
composition causes recomposition every time the state changes. The same read deferred to the
layout or draw phase avoids recomposition entirely.

> **Routing:** Use this workflow when an audit finding points to a composable recomposing on
> every frame, animation tick, or scroll event, or when building/reviewing a custom modifier.

## Workflow

### Phase 1 — Measure

- [ ] **1. Identify the suspect modifier.** Look for modifiers that read state:

```kotlin
// Suspect — reads state during composition
Modifier.offset(x = offsetX.value.dp)
Modifier.graphicsLayer { translationX = offsetX.value } // OK — draw phase
```

- [ ] **2. Add logging to the composable.**

```kotlin
@Composable
fun MovingBox(offsetX: State<Float>) {
    SideEffect { println("[Modifier] MovingBox recomposed at ${offsetX.value}") }
    Box(modifier = Modifier.offset(x = offsetX.value.dp))
}
```

If the log prints on every animation frame, the modifier reads state in composition phase.

- [ ] **3. Check modifier order.** Compose applies modifiers outside-in.

For the full order rules, read [modifier-order.md](modifier-order.md).

### Phase 2 — Diagnose

- [ ] **4. Determine which phase the state read occurs in.**

| Modifier | Phase | Recomposes on change? |
|----------|-------|----------------------|
| `Modifier.offset(x.dp)` | Composition | Yes |
| `Modifier.offset { IntOffset(x, y) }` | Layout | No |
| `Modifier.graphicsLayer { ... }` | Draw | No |
| `Modifier.drawBehind { ... }` | Draw | No |
| `Modifier.layout { ... }` | Layout | No |
| `Modifier.composed { ... }` | Composition | Yes (if state read inside) |

For the phase deferral guide, read [phase-deferral.md](phase-deferral.md).

- [ ] **5. Check for `composed { }` misuse.** `composed { }` runs during composition. Correct for
  theme-dependent modifiers; incorrect for state that changes frequently.

- [ ] **6. Check custom modifier implementation strategy.**

| Strategy | When to use | Overhead |
|----------|-------------|----------|
| Extension function on `Modifier` | Stateless, pure transformation | Zero |
| `composed { }` | Needs `LocalDensity`, theme values, or one-shot state | Low |
| `Modifier.Node` | Stateful, observes changing state without recomposition | Lowest |

For the custom modifier guide, read [custom-modifier-node.md](custom-modifier-node.md).

### Phase 3 — Fix

- [ ] **7. Move state reads from composition to layout or draw.**

```kotlin
// WRONG — recomposes every frame
Modifier.offset(x = offsetX.value.dp)

// RIGHT — reads in layout phase, no recomposition
Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
```

```kotlin
// WRONG — recomposes every frame
Modifier.rotate(rotation.value)

// RIGHT — reads in draw phase, no recomposition
Modifier.graphicsLayer { rotationZ = rotation.value }
```

- [ ] **8. Replace `composed { }` with `Modifier.Node` for frequently-changing state.**

```kotlin
// WRONG — reads State.value at composition time → recomposes on every alpha change
fun Modifier.fade(alpha: State<Float>) = composed {
    this.alpha(alpha.value) // composition-phase read
}
```

For simple visual properties (alpha, rotation, scale, translation), the draw-phase shortcut
is sufficient — no custom node needed:

```kotlin
// RIGHT (simple) — graphicsLayer lambda runs in draw phase; no recomposition
fun Modifier.fade(alpha: State<Float>): Modifier =
    graphicsLayer { this.alpha = alpha.value }
```

For a reusable custom modifier, use the full `Modifier.Node` pattern:

```kotlin
// RIGHT (custom node) — state read is in draw phase; no recomposition, no allocation
fun Modifier.fade(alpha: State<Float>): Modifier = this.then(FadeElement(alpha))

private data class FadeElement(val alpha: State<Float>) : ModifierNodeElement<FadeNode>() {
    override fun create() = FadeNode(alpha)
    override fun update(node: FadeNode) { node.alpha = alpha }
}

private class FadeNode(var alpha: State<Float>) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContext.canvas.saveLayer(size.toRect(), Paint().apply { this.alpha = alpha.value })
        drawContent()
        drawContext.canvas.restore()
    }
}
```

- [ ] **9. Fix modifier order.**

```kotlin
// WRONG — background fills the full area; padding only insets the content on top of it
Modifier.fillMaxSize().background(Color.Red).padding(16.dp)

// RIGHT — background drawn only inside the padded bounds
Modifier.fillMaxSize().padding(16.dp).background(Color.Red)
```

### Phase 4 — Verify

- [ ] **10. Re-run logging.** Composable should not log during animation/scroll after the fix.
- [ ] **11. Verify visual correctness.** UI should look identical.
- [ ] **12. Profile CPU during animation.** Time in `ComposerKt` should drop.

## Patterns

### Defer state read to draw phase

```kotlin
// WRONG — recomposes every frame
Box(modifier = Modifier.offset(x = scrollOffset.value.dp))

// RIGHT — draw phase read, zero recomposition
Box(modifier = Modifier.graphicsLayer { translationX = scrollOffset.value })
```

### Defer state read to layout phase

```kotlin
// WRONG — recomposes every frame
Box(modifier = Modifier.height(listHeight.value.dp))

// RIGHT — layout phase read
Box(modifier = Modifier.layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(maxHeight = listHeight.value.roundToInt())
    )
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
})
```

### Reusable modifier factory

```kotlin
// WRONG — allocates a new object every call
fun Modifier.bordered(borderWidth: Dp, color: Color) = composed { border(borderWidth, color) }

// RIGHT — pure extension, zero allocation
fun Modifier.bordered(borderWidth: Dp, color: Color): Modifier =
    this.then(BorderModifier(borderWidth, color))
```

## Mandatory rules

- **MUST** measure before rewriting modifiers. Logging `SideEffect` is enough.
- **MUST** move frequently-changing state reads out of the composition phase.
- **MUST NOT** use `composed { }` for modifiers that read rapidly-changing state.
- **MUST** prefer `Modifier.Node` over `composed { }` for stateful custom modifiers.
- **MUST** apply layout modifiers (`padding`, `size`, `fillMaxSize`) before draw modifiers
  (`background`, `border`, `clip`).
- **MUST** apply pointer-input modifiers (`clickable`, `hoverable`) after layout modifiers.
- **MUST NOT** read `State<T>.value` directly in a modifier extension unless the state changes
  rarely (e.g. theme switches).
- **MUST** verify visual correctness after phase deferral.

## Verification checklist

- [ ] Logging confirms composable no longer recomposes on every frame/animation/scroll.
- [ ] CPU profiling shows reduced time in `ComposerKt` during the affected interaction.
- [ ] Visual output is identical before and after the fix.
- [ ] Custom modifiers use `Modifier.Node` instead of `composed { }` when state is involved.
- [ ] Modifier order follows: layout → draw → pointer input.

## References

- [phase-deferral.md](phase-deferral.md) — composition vs layout vs draw, per-modifier phase map
- [modifier-order.md](modifier-order.md) — outside-in application order, common ordering mistakes
- [custom-modifier-node.md](custom-modifier-node.md) — `Modifier.Node` lifecycle, node types
