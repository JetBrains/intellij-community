# Kotlin UI DSL

The DSL builds a form by calling into a builder; here the form *is* the composition. Two consequences run
through everything below:

- **Visibility and enabled state differ.** A conditional can replace `visibleIf` when removing the control preserves its state contract.
  Use `SwingModifier.visible(...)` when the hidden control must keep its state and identity.
  Use `SwingModifier.enabled(...)` for one control or `ProvideComponentDefaults(DefaultEnabled provides enabled)` for a composed region. Disable children inside a hosted Swing container through its own enabled contract.
- **Binding is state, not a call.** `bindSelected(prop)` becomes `bind(prop)` on the configurable, read and
  written as an ordinary `var`.

```kotlin
// DSL
panel {
  row { checkBox("Enable").bindSelected(settings::enabled) }
  row("Port:") { spinner(1024..65535).bindIntValue(settings::port) }.visibleIf(enabledPredicate)
}

// Compose
private var enabled by bind(settings::enabled)
private var port by bind(settings::port)

FormPanel {
  FormRow { CheckBox(text = "Enable", checked = enabled, onCheckedChange = { enabled = it }) }
  if (enabled) {
    FormRow(label = "Port:") { Spinner(value = port, onValueChange = { port = it.toInt() }, min = 1024, max = 65535) }
  }
}
```

## The settings page

```kotlin
class MySettingsConfigurable : ComposeSwingSearchableConfigurable() {
  private val settings = MySettings.getInstance()
  private var enabled by bind(settings::enabled)
  private var port by bind(settings::port)

  override fun getId(): String = "com.example.settings"
  override fun getDisplayName(): String = MyBundle.message("configurable.name")

  @Composable
  override fun ComposeContent() {
    FormPanel {
      FormRow { CheckBox(text = MyBundle.message("enable"), checked = enabled, onCheckedChange = { enabled = it }) }
      FormRow(label = MyBundle.message("port")) {
        Spinner(value = port, onValueChange = { port = it.toInt() }, min = 1024, max = 65535)
      }
    }
  }
}
```

Register it in `plugin.xml` as any other `Configurable`.

### Form state and previews

Keep the model in the configurable. Pass values and event callbacks to the form.
Use the same form in tests and previews:

```kotlin
@Composable
private fun OptionsForm(enabled: Boolean, onEnabledChange: (Boolean) -> Unit, modifier: SwingModifier = SwingModifier) {
  FormPanel(modifier) {
    FormRow { CheckBox(text = MyBundle.message("enable"), checked = enabled, onCheckedChange = onEnabledChange) }
  }
}

@Preview
@Composable
private fun OptionsPreview() {
  var enabled by remember { mutableStateOf(true) }
  OptionsForm(enabled, { enabled = it })
}
```

Import `org.jetbrains.compose.swing.tooling.Preview`. Preview functions take no parameters.
Open the file in the IDE and refresh the Compose UI Preview tool window.
The running IDE must contain the APIs the form uses.

Create one composition at the page boundary. Child forms share that composition and take no disposable.
A migration creates no new components. A control that no library or IntelliJ component covers becomes a
reusable composable that follows the library's
[custom components](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/CUSTOM-COMPONENTS.md) guide.

### Binding

`bind(...)` returns a `SettingState<T>`: a Compose state seeded from the stored value, which is the single
source of truth for the UI. The base class collects every binding and derives `isModified`, `apply` and
`reset` from them, so a page that only edits persisted settings overrides none of the three.

`SettingState` is a **working copy**. Editing it does not touch the store; `apply()` writes through and
`reset()` reloads. `isModified` compares the working value against the *current* stored value, so a setting
changed elsewhere while the page is open also counts as modified. That is the `Configurable` contract.

For a setting with no backing property, use the computed form:

```kotlin
private var shown by bind(get = { !Dismissal.isDismissed() }, set = { Dismissal.setDismissed(!it) })
```

A page with extra behavior overrides `isModified`/`apply`/`reset` and calls `super`.

### Lifecycle

`createComponent()` is `final`. It gives the page a runtime of its own and composes `ComposeContent()` into it
on the call. It also gives the page its margins.

The base class implements `Configurable.NoMargin`. The margins go on the top-level component that the
composition creates, not on the component around it. The platform grid needs the insets of the component it lays out.

The page composes on the call because the Settings dialog builds and searches a page without showing it.
A `composeSwingPanel` host composes only when it reaches a window, so it would leave an empty panel.

`getContent()` returns the built page, and throws before it is built.

`disposeUIResources()` disposes the composition and the runtime under it. **An override must call `super`.**
The Settings dialog builds a page again after disposing it, so disposal leaves the page buildable; each
`createComponent()` gets a composition of its own.

### Telling the dialog something changed

The dialog re-reads `isModified` off input gestures. A page whose state moves on its own, when a background
detection finishes or a service pushes an update, has to say so, or Apply and Reset stay disabled:

```kotlin
LaunchedEffect(Unit) {
  clients = detectClients()
  requestModifiedCheck()
}
```

### Refusing a value

Throw `ConfigurationException` from `apply()` to keep the dialog open when a value is invalid. Preserve input-time checks and the owner's existing validation calls during migration. Use the error-outline modifier to mark a field. A page that requires a validation API must provide that API before migration.

```kotlin
override fun apply() {
  if (port !in 1024..65535) throw ConfigurationException(MyBundle.message("error.port.range"))
  super.apply()
}
```

To mark the offending field as well, use the outline modifier:

```kotlin
TextField(
  value = host,
  onValueChange = { host = it },
  modifier = SwingModifier.errorOutline(host.isBlank()),
)
```

### Settings search

Search indexes a page written this way, and spotlight highlights into it.

The traversal reads what the page holds at that moment: **content the page does not compose is not
indexed.** A group gated behind a setting being on is absent from the index while it is off, exactly as a
conditionally built `panel { }` row would be. Index those strings explicitly if they must be findable,
with a `SearchableOptionContributor` or the additional-search-labels client property on the page
component.

### Focus

`getPreferredFocusedComponent()` delegates to `IdeFocusTraversalPolicy`. Override it if the field to start
in is not the one that picks.

## Migrating one row at a time

`composeSwingPanel` returns an ordinary `JComponent`, so it goes in a cell and the rest of the form stays as it
is:

```kotlin
panel {
  row("Name:") { textField() }
  row {
    cell(composeSwingPanel(parentDisposable) { ServerStatus(service) })
      .align(AlignX.FILL)
  }
}
```

Pick the disposable of the form around it: the `DialogWrapper`'s, the page's, or the tool window content.
Two consequences decide whether a settings page should be split this way at all:

- **What the host composes is not indexed by settings search.** The host waits for a
  window, and the traversal walks the form before it is shown. A page whose options must be findable
  composes as a whole, through `ComposeSwingSearchableConfigurable`.
- **`visibleIf` on the row does not detach the host,** so a gated row keeps its composition and its
  state.

## Every `panel { }` construct

### `Panel`

| DSL | Compose | Notes |
|---|---|---|
| `row(label, init)` | `FormRow(label) { }` | |
| `row(JLabel, init)` | `FormRow { }` + `SwingNode` for the label | No overload taking a component label |
| `indent { }` | `FormIndent { }` | |
| `separator()` | `FormSeparator()` | The `background` parameter has no counterpart |
| `group(title, init)` | `FormGroup(title) { }` | `indent = false` for a group whose rows are not indented |
| `group(JBLabel, indent, init)` | `FormGroup(title) { }` | No component-title overload |
| `groupRowsRange(...)` | `FormGroup(...) { }` | `FormGroup` has a grid of its own, but `groupRowsRange` shares the grid of the parent. Rows of the group no longer line up with the rows outside it. For a shared grid, use `FormIndent { }`. Range operations are conditionals here |
| `rowsRange { }` | plain Kotlin `if` / a helper composable | Exists only to gate a set of rows |
| `panel { }` (nested) | a nested `FormPanel`, or just more rows | |
| `twoColumnsRow` / `threeColumnsRow` | `FormRow { }` with several cells | The row's cells are its columns |
| `collapsibleGroup(...)` | **gap** | No equivalent. `CollapsibleRow` is a DSL row, not a component, so `SwingNode` cannot host it. Keep the group in `panel { }`, or build the toggle by hand |
| `buttonsGroup(...)` | **gap** | The library has `RadioGroup`, but not the bind-to-property form |
| `onApply` / `onReset` / `onIsModified` | override `apply` / `reset` / `isModified` | The configurable owns these |
| `customizeSpacingConfiguration` | **gap** | The form uses `IntelliJSpacingConfiguration` |

### `Row`

| DSL | Compose | Notes |
|---|---|---|
| `cell(component)` | `SwingNode(factory = { component })` | |
| `cell()` | omit the cell | |
| `scrollCell(component)` | `ScrollPane { }` | |
| `placeholder()` | **obsolete by design** | Conditional content is native |
| `panel { }` | a nested `FormPanel` | |
| `layout(RowLayout)` | - | The form derives the layout from the row |
| `resizableRow()` | `FormRow(resizable = true)` | |
| `rowComment(...)` | `FormRow(comment = ...)` | `FormComment(...)` is a row holding nothing but a comment |
| `visible` / `visibleIf` | `if (condition)`, or `SwingModifier.visible(...)` | |
| `enabled` / `enabledIf` | `SwingModifier.enabled(...)` | Per component |
| `topGap(TopGap.X)` / `bottomGap(BottomGap.X)` | `FormRow(topGap = FormGap.X)` / `bottomGap = FormGap.X` | `FormGap` names the same NONE/SMALL/MEDIUM steps. A dropped gap is invisible in a diff: the rows still line up but stand closer together. Read the gaps off every row of the original page |
| `customize(UnscaledGapsY)` | **gap** | Only the named steps; a row wanting an exact distance stays with `panel {}` |
| `checkBox(text)` | `CheckBox` | |
| `threeStateCheckBox(text)` | `ThreeStateCheckBox` | |
| `radioButton(text, value)` | `RadioButton` / `RadioGroup` | |
| `button(text, actionListener)` | `Button` | |
| `button(text, action, place)` | `Button` + `SwingNode`, or `OptionButton` | No `AnAction` overload |
| `segmentedButton(items, renderer)` | `SegmentedButton` | |
| `slider(...)` | `Slider` | |
| `label(text)` | `Label` | |
| `text(text, maxLineLength, action)` | `Label` for plain text | `Comment` is dimmed. `Comment` has no `action` parameter: it opens `<a href>` links in a browser. **Gap** for a custom link action; use `ActionLink` or `SwingNode` |
| `comment(comment, maxLineLength, action)` | `Comment` | By default it wraps to the width its row has left. Pass `maxLineLength` for a fixed width. **Gap** for `action`, as in `text` |
| `link(text, action)` | `ActionLink` | |
| `browserLink(text, url)` | `BrowserLink` | |
| `dropDownLink(item, items)` | `DropDownLink` | |
| `icon(icon)` | `SwingModifier.icon(...)` | `AllIcons` directly, with no icon-key indirection |
| `contextHelp(description, title)` | `SwingNode(factory = { ContextHelpLabel.create(...) })` | Deliberately unwrapped |
| `textField()` | `TextField` | |
| `passwordField()` | `PasswordField` | |
| `textArea()` | `ScrollPane { TextArea(...) }` | The DSL wraps the area in a scroll pane, but `TextArea` does not |
| `intTextField(range, keyboardStep)` | **gap** | `Spinner` for a bounded number, otherwise `SwingNode` over `JBTextField` |
| `spinner(range, step)` | `Spinner` | |
| `comboBox(model / items, renderer)` | `ComboBox` | |
| `textFieldWithBrowseButton(...)` (4 overloads) | **gap** | `SwingNode` over the platform component |
| `expandableTextField(...)` | **gap** | `SwingNode` |
| `extendableTextField()` | **gap** | `SwingNode` |

### `Cell`

| DSL | Compose | Notes |
|---|---|---|
| `align(AlignX.*)` | `SwingModifier.align(FormAlignX.*)` | The default is `FormAlignX.LEFT` |
| `align(AlignY.*)` | `SwingModifier.align(FormAlignY.*)` | The default is `FormAlignY.CENTER` |
| `align(Align.FILL)` | `SwingModifier.align(FormAlignX.FILL).align(FormAlignY.FILL)` | The two calls combine on one chain |
| `resizableColumn()` | `SwingModifier.resizableColumn()` | The column takes spare horizontal space |
| `gap(rightGap)` | `SwingModifier.gap(FormRightGap.*)` | `FormRightGap` names `SMALL` and `COLUMNS`. Copy `FormRightGap.SMALL` between an icon and its text |
| `customize(UnscaledGaps)` | **Gap** | No exact-gap override. Use `gap` or `FormRow` gaps, or keep the row in `panel {}` |
| `label(text, position)` | `FormRow(label = text, labelPosition = FormLabelPosition.X)` | The default position is `FormLabelPosition.LEFT` |
| `comment(...)` / `commentRight(...)` | `FormRow(comment = ..., commentMaxLineLength = ...)` | **Gap** for `commentRight`: a `Comment` placed as a second control gets the default gap, not the comment gap, and does not describe the control for accessibility |
| `focused()` | `SwingModifier.initialFocus()` | |
| `applyToComponent { }` | the `update` block of `SwingNode` | |
| `bold()` | `SwingModifier.font(...)` | |
| `accessibleName` / `accessibleDescription` | the library's accessibility modifiers | |
| `contextHelp(...)` | `SwingNode(factory = { ContextHelpLabel.create(...) })` | |
| `applyIfEnabled()` | **gap** | `apply()` writes every binding, also the binding of a disabled control. Use the computed `bind(get, set)` and check the enabled condition in `set` |
| `onChanged` / `onChangedContext` | the component's own callback | |
| `onApply` / `onReset` / `onIsModified` | override on the configurable | |
| `bind(get, set, prop)` and every `bind*` extension | `bind(...)` on the configurable | `bindSelected`, `bindText`, `bindIntText`, `bindItem`, `bindValue`, `bindIntValue`, `bindState` |
| `widthGroup(group)` | `SwingModifier.widthGroup(group)` | Controls in the group have the same width. A cell cannot also use `FormAlignX.FILL` |
| `validationOnApply` / `errorOnApply` / `addValidationRule` | `ConfigurationException` from `apply` | See [Refusing a value](#refusing-a-value) |
| `validationOnInput` / `validationRequestor` / `cellValidation` / `validationInfo` / `validation` | **gap** | No on-input validation framework; `SwingModifier.errorOutline(...)` for the look |

### `RowsRange` and `ButtonsGroup`

| DSL | Compose |
|---|---|
| `RowsRange.visible` / `visibleIf` | a conditional, or visibility modifiers when the controls must retain their state |
| `RowsRange.enabled` / `enabledIf` | enabled properties or `SwingModifier.enabled(...)` on the affected components |
| `ButtonsGroup.bind(prop, type)` | **gap**. `RadioGroup` from the library, bound by hand |
