# Components Catalog

This is a practical catalog of Jewel UI components for choosing the right primitive quickly.

## How To Use This Catalog

1. Pick the component category.
2. Choose the simplest component that matches the interaction.
3. Apply defaults first; customize via `JewelTheme.<component>Style` only when needed.
4. Cross-check composition patterns in `LAYOUT-PATTERNS.md`.

## Core Visual/Text

1. `Text`: themed text rendering.
2. `Icon`: icon rendering with `IconKey`.
3. `Image`: image rendering from `IconKey` with layout controls.
4. `Divider`: horizontal/vertical separators.
5. `GroupHeader`: section headers in grouped UIs.
6. `InfoText`: inline info/warning/error text patterns.

## Actions And Buttons

1. `DefaultButton` / `OutlinedButton`: primary and secondary actions.
2. `DefaultSplitButton` / `OutlinedSplitButton`: action + dropdown menu.
3. `IconButton`, `IconActionButton`, `SelectableIconActionButton`, `ToggleableIconActionButton`: icon-first actions.
4. `ActionButton`: action semantics wrapper.
5. `Chip`: clickable chip action.

## Input And Selection

1. `Checkbox`, `CheckboxRow`, `ToggleableChip`: boolean and tri-state style interactions.
2. `RadioButton`, `RadioButtonRow`, `RadioButtonChip`: exclusive selection.
3. `TextField`, `TextArea`: text input variants. Both support a `TextFieldState`-based overload and a `value` / `onValueChange` overload — pick the one matching the Compose convention used in the surrounding code.
4. `Slider`: continuous numeric input.
5. `SegmentedControl`, `SegmentedControlButton`: segmented option pickers.
6. `Tabs`, `TabStrip`: tabbed content.
7. `ComboBox`, `ListComboBox`, `EditableComboBox`: dropdown selection/editable combo flows.
8. `Dropdown`: compact menu-like selection.

## Menus, Popups, And Contextual UI

1. `Menu`, `PopupMenu`, `ContextMenu`, `TextContextMenu`: menu systems.
2. `Popup`, `PopupContainer`: anchored and general popup containers.
3. `PopupAd`: promotional popup blocks.
4. `Tooltip`, `TooltipArea`: hover/help affordances.

## Feedback And Status

1. `CircularProgressIndicator`: indeterminate or compact progress.
2. `HorizontalProgressBar` and `IndeterminateHorizontalProgressBar`: horizontal progress variants.
3. `DefaultBanner`, `InlineBanner`: status messaging and callouts.
4. `Link`: actionable hyperlinks.

## Lists, Trees, Layout Helpers

1. `SimpleListItem`: list row building block.
2. `LazyTree` (and speed-search wrappers): tree navigation.
3. `SplitLayout`: resizable split panes.
4. `VerticalScrollbar`, `HorizontalScrollbar`, `VerticallyScrollableContainer`, `HorizontallyScrollableContainer`: scrolling primitives and styling.
5. `SpeedSearchArea`: search affordance for searchable lists/trees.

## Search-Oriented Variants

Look in `component/search` for:

1. `SpeedSearchScope.SpeedSearchableComboBox`
2. `SpeedSearchScope.SpeedSearchableLazyColumn`
3. `SpeedSearchScope.SpeedSearchableTree`

Use when keyboard-first filtering and indexed search interactions are required. These are used inside a `SpeedSearchArea` scope.

## Window Decoration

1. `DecoratedWindow`: window with custom title bar support.
2. `TitleBar`: custom title bar area.

## Markdown Rendering

1. `Markdown`: render markdown content.
2. `LazyMarkdown`: render large markdown documents efficiently.

## Composition Example

```kotlin
Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    GroupHeader("Profile")
    TextField(state = nameState, modifier = Modifier.fillMaxWidth())

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
        DefaultButton(onClick = onSave) { Text("Save") }
    }

    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
    ListComboBox(items = roles, selectedIndex = roleIndex, onSelectedItemChange = { roleIndex = it })
}
```

## Guidance

1. Start from the component list used in showcase samples for reliable defaults.
2. Prefer Jewel components over raw Compose widgets for consistent Int UI behavior.
3. Keep state outside components (viewmodel/state holder), pass state in.
4. Use style accessors (`JewelTheme.<component>Style`) instead of hardcoded visuals.
5. Avoid internal manager types (`*Manager`, `*Controller`) unless implementing advanced plumbing.

## Canonical Source Links

- [UI component directory](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component)
- [Search components directory](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component/search)
- [Component showcase registry](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/views/ComponentsViewModel.kt)
- [Showcase screen composition](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/views/ComponentsView.kt)
- [Theme style accessors](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/theme/JewelTheme.kt)
