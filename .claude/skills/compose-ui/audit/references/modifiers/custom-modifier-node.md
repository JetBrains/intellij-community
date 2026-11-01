# Custom Modifier.Node

`Modifier.Node` is the lowest-overhead way to write stateful custom modifiers. It replaces the
older `Modifier.composed { }` and `LayoutModifier` interfaces.

## When to use Modifier.Node

| Scenario | Use `Modifier.Node`? |
|----------|---------------------|
| Stateless transformation (e.g. add padding) | No — use extension function. |
| Needs `LocalDensity` or theme value once | No — use `composed { }`. |
| Observes a `State<T>` that changes frequently | Yes — `Modifier.Node` avoids recomposition. |
| Custom draw logic that depends on changing state | Yes — use `DrawModifierNode`. |
| Custom layout logic that depends on changing state | Yes — use `LayoutModifierNode`. |
| Custom pointer input | Yes — use `PointerInputModifierNode`. |

## Minimal example: DrawModifierNode

The element and the node are required together. The element is the immutable description; the
node is the mutable runtime instance that survives recompositions.

```kotlin
fun Modifier.fade(alpha: State<Float>): Modifier = this.then(FadeNodeElement(alpha))

private data class FadeNodeElement(
    val alpha: State<Float>,
) : ModifierNodeElement<FadeNode>() {
    override fun create() = FadeNode(alpha)
    override fun update(node: FadeNode) {
        node.alpha = alpha
    }
}

private class FadeNode(
    var alpha: State<Float>,
) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        // Reading alpha.value here is a draw-phase read.
        // Compose re-draws when the state changes — no recomposition.
        val currentAlpha = alpha.value
        if (currentAlpha >= 1f) {
            drawContent()
        } else {
            drawContext.canvas.saveLayer(
                bounds = size.toRect(),
                paint = Paint().apply { this.alpha = currentAlpha },
            )
            drawContent()
            drawContext.canvas.restore()
        }
    }
}
```

For simple properties (alpha, rotation, scale, translation), prefer the `graphicsLayer` shortcut
over a full custom node:

```kotlin
// Simpler for common visual properties — no node boilerplate needed
fun Modifier.fade(alpha: State<Float>): Modifier =
    graphicsLayer { this.alpha = alpha.value }
```

## Key concepts

- **`ModifierNodeElement`** — the immutable description of the modifier. Compose uses `equals`
  and `hashCode` to decide whether to update or replace the node. Always implement both on
  data classes or explicitly.
- **`Modifier.Node()`** — the mutable runtime instance. Lives across recompositions if the
  element is equal.
- **`update()`** — called when the element changes but the node is reused. Update internal state
  here; do not allocate new objects.
- **`onAttach()` / `onDetach()`** — lifecycle callbacks. Use for registering/unregistering
  observers.

## Node types

| Type | Use for |
|------|---------|
| `DrawModifierNode` | Custom `drawBehind`, `drawWithContent`, alpha, rotation. |
| `LayoutModifierNode` | Custom measure/place logic. |
| `PointerInputModifierNode` | Custom gesture handling. |
| `SemanticsModifierNode` | Accessibility semantics. |

## Common mistakes

- **Forgetting `hashCode`/`equals` on the element.** Compose cannot diff modifiers; it recreates
  the node on every recomposition.
- **Allocating in `update()`.** `update()` should mutate the node, not create new objects.
- **Reading `State.value` in `create()`.** The state may change; store the `State` object and read
  `.value` in `draw()` or `measure()`.
