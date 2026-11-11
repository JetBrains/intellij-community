# compose-swing-ui in the IntelliJ Platform

Write IDE forms and settings pages declaratively, with the Compose runtime driving **real Swing
components**. The composition creates no drawing surface of its own, so everything that walks the Swing
tree keeps working: layout, focus traversal, settings search and accessibility. The page takes its
styling from the IDE look and feel.

The library is [JetBrains/compose-swing-ui](https://github.com/JetBrains/compose-swing-ui). Its
[docs](https://github.com/JetBrains/compose-swing-ui/tree/v0.1.0-alpha06/docs) and
[API reference](https://jetbrains.github.io/compose-swing-ui/) carry the vocabulary: the runtime, the
components, the modifiers, `SwingNode` and the test harness. This module is the IntelliJ side: the IDE
components the library cannot know about, the settings-page base class, and the rules that come with
running inside the IDE.

```kotlin
class MySettingsConfigurable : ComposeSwingSearchableConfigurable() {
  private val settings = MySettings.getInstance()
  private var enabled by bind(settings::enabled)

  override fun getId(): String = "com.example.settings"
  override fun getDisplayName(): String = MyBundle.message("configurable.name")

  @Composable
  override fun ComposeContent() {
    FormPanel {
      FormRow {
        CheckBox(text = MyBundle.message("enable"), checked = enabled, onCheckedChange = { enabled = it })
      }
    }
  }
}
```

## When to use it

Use it for forms and settings pages, and for anything you would otherwise write with `panel { }`.

## Threading

Composable code runs on the EDT, and no read or write lock is held. Reach PSI, VFS, documents and the
project model from a `LaunchedEffect`:

```kotlin
LaunchedEffect(file) {
  name = readAction { ModuleUtilCore.findModuleForFile(file, project)?.name }
}
```

## Hosting a composition

```kotlin
val panel: JComponent = composeSwingPanel(parentDisposable) {
  Label(text = "Hello")
}
```

`composeSwingPanel` returns a `JComponent` that hosts the composition. Call it on the EDT, and dispose
`parentDisposable` on the EDT. Only `parentDisposable` ends the composition. A host that leaves the Swing
hierarchy and comes back keeps its state.

The host is the root of the content's layout. Each top-level composable is measured under the host's size
and placed at its top-start corner, so `fillMaxWidth`, `width` and `wrapContentHeight` work at the top
level. The host implements `WidthBasedLayout`: an IDE layout that asks for the height at a width gets the
content's height at that width.

The library's `JComponent.setContent` is the built-in alternative. It returns a `DisposableHandle`, and the
caller disposes it.

A host composes when it reaches a window, so an
off-screen traversal, such as settings search, reads an empty panel. A `ComposeSwingSearchableConfigurable` composes when it is built, so settings search finds its
options. See [kotlin-ui-dsl.md](docs/kotlin-ui-dsl.md).

## Popups

```kotlin
val popup = JBPopupFactory.getInstance().createComposeSwingPopup(
  positionProvider = AnchorAlignmentPositionProvider(anchorAlignment = Alignment.TopEnd, popupAlignment = Alignment.BottomEnd),
  configure = { setRequestFocus(true) },
) {
  MyPopupContent(modifier = SwingModifier.width(JBUI.scale(400)))
}
popup.show(component)
```

`createComposeSwingPopup` creates the popup and composes its content. The caller closes the popup with `popup.cancel()`. The popup takes the size of the content and follows it when the content changes, so the content sets its limits with layout modifiers. The `PopupPositionProvider` places the popup against the component it is shown for, as the `Popup` of androidx Compose does. For a popup that the platform builds, such as a documentation popup, `composeSwingPopupContent` returns the content component, and the platform sizes the popup.

## What this module adds

Components, layout panels and modifiers come from the library, cataloged in
[COMPONENTS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/COMPONENTS.md). Reach for
those first: a plain `JCheckBox` already looks like an IDE checkbox.

What lives here is only what the library cannot express, an IntelliJ component with behavior of its
own. Those wrappers are in `com.intellij.platform.compose.swing.components`, and their KDoc is the
catalog. A wrapper that replaces a library component takes the name of that component, and its package mirrors the package of that component. For example, `ScrollPane` on a `JBScrollPane` is in `components.layout`. A component with no wrapper is hosted directly; see [raw-swing.md](docs/raw-swing.md).

Forms are `FormPanel` rather than a component, laid out by the platform grid engine that `panel { }` uses.

Every wrapper hoists its state: the caller owns the value and gets a callback. A pick the caller does not
adopt is undone on the pass that follows it.

## Testing

Tests use the library's `swing-ui-test` harness (`runComposeSwingTest`), described in
[TESTING-COMPONENTS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/TESTING-COMPONENTS.md).
Two of its limits matter in the IDE:

- The harness installs no IDE look and feel, so a component whose UI delegate comes from the IntelliJ LaF
  is built without one. Assert on text, model and structure, not on painting or preferred size.
- The harness composes the content in an off-screen root, and `addNotify` and `removeNotify` fire on
  it. That root is never showing and never owns focus. Test attach-scoped behavior that depends on
  `addNotify`. Behavior that needs a showing or focused window cannot run there.

## Neighboring stacks

| | |
|---|---|
| [raw-swing.md](docs/raw-swing.md) | Platform components in a composition, and a composition in a Swing container |
| [kotlin-ui-dsl.md](docs/kotlin-ui-dsl.md) | Settings pages: the `Configurable` contract, and every `panel { }` construct |
| [compose-multiplatform.md](docs/compose-multiplatform.md) | Porting code from Compose Multiplatform or Jewel: what carries over and what does not |
