# Raw Swing

Both directions cross without a bridge layer: a composition is real Swing components, and a Swing
component hosted in a composition is the component itself. A screen can therefore be converted a region
at a time, in either direction.

## A composition in a Swing container

`composeSwingPanel` returns the host:

```kotlin
val status: JComponent = composeSwingPanel(parentDisposable) { ServerStatus(service) }
```

The host survives when it is taken out of the hierarchy and put back. It ends when `parentDisposable` is
disposed. Pick the disposable of the screen around it: the `DialogWrapper`'s, the tool window content, or
the page's. It composes when it reaches a window, so a traversal that runs before the container is shown
reads an empty panel (see the [README](../README.md)). Hiding the host does not detach it, so a hidden
region keeps its composition and its state.

For a composition inside a `panel { }` form, see
[kotlin-ui-dsl.md](kotlin-ui-dsl.md#migrating-one-row-at-a-time).

## A platform component in a composition

Host it with the library's `SwingNode`, documented in
[CUSTOM-COMPONENTS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/CUSTOM-COMPONENTS.md).

```kotlin
SwingNode(
  factory = { JBLabel() },
  update = { set(moduleName) { this.text = it } },
)
```

A component that holds no changing value needs no update block. `ContextHelpLabel` is one: it fixes its
help text in a `HelpTooltip` at construction.

```kotlin
SwingNode(factory = { ContextHelpLabel.create(MyBundle.message("help")) })
```

Editors, trees and tables with async models, toolbars and tab containers are coupled to platform
services with a lifetime of their own. The two rules below cover them.

### Anything that needs a `Disposable`

Pair one with the composition through `DisposableEffect`. There is no `rememberDisposable`; writing the
effect out is the supported form.

```kotlin
if (visible) {
  DisposableEffect(Unit) {
    val disposable = Disposer.newDisposable("my node")
    service.subscribe(disposable) { ... }
    onDispose { Disposer.dispose(disposable) }
  }
}
```

Content that leaves and comes back gets a **fresh** disposable; the disposed one is never reused.

### `ActionToolbar`

Set the target component. A toolbar without one resolves its actions against whatever holds focus, so the
actions on a composed page would be evaluated against the wrong context.

```kotlin
SwingNode(
  factory = {
    ActionManager.getInstance()
      .createActionToolbar(place, group, true)
      .apply { targetComponent = page }
      .component
  },
)
```
