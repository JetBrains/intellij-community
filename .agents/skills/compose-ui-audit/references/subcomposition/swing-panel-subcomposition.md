# SwingPanel Subcomposition

`SwingPanel` embeds a Swing `JComponent` inside Compose Desktop. It uses subcomposition internally
to bridge Compose layout constraints to Swing component bounds.

## Factory stability

The `factory` lambda is called during subcomposition. A new lambda object on every recomposition
causes a new Swing component to be allocated and replaced:

```kotlin
// WRONG — new JLabel every recomposition
SwingPanel(
    factory = { JLabel(text) },
    modifier = modifier,
)
```

```kotlin
// RIGHT — stable factory, update via update block
val panel = remember { JLabel() }
SwingPanel(
    factory = { panel },
    modifier = modifier,
    update = { it.text = text },
)
```

## Update block

The `update` block runs after subcomposition but does not recreate the Swing component. Use it
for all dynamic properties:

```kotlin
val textArea = remember { JTextArea() }
SwingPanel(
    factory = { textArea },
    modifier = Modifier.fillMaxSize(),
    update = { ta ->
        ta.text = content
        ta.isEditable = isEditable
    },
)
```

## Resize thrash

`SwingPanel` remeasures on every size change. If the Swing component does expensive layout
(e.g. `JTable` with many rows, `JTextArea` with word wrap), resize thrash occurs.

Mitigations:
1. **Constrain size** — give `SwingPanel` a fixed or bounded size instead of `fillMaxSize()`.
2. **Defer Swing layout** — set `panel.layout = null` and manage bounds manually if the component
   supports it.
3. **Cache heavy data** — ensure the Swing model (`TableModel`, `Document`) is not rebuilt on
   every update.

## Focus

Swing focus and Compose focus are separate systems. `SwingPanel` does not automatically bridge
focus. If focus behaviour is wrong, the issue is likely outside subcomposition — check
`LocalFocusManager` and Swing `FocusTraversalPolicy` separately.
