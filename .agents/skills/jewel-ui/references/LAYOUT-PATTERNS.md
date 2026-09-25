# Layout Composition Patterns

These patterns come from the [showcase sample](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/samples/showcase) and the [standalone sample](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/samples/standalone).

## Pattern 1: Shell with navigation rail + content panel

Use a top-level `Row` with:
1. Left navigation (`Column` with icon action buttons).
2. Vertical divider.
3. Main content column.

Reference:
- [ComponentsView.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/views/ComponentsView.kt)

## Pattern 2: Stateful toolbar driving composable screens

Use a `ViewModel` with:
1. `SnapshotStateList<ViewInfo>` holding title, icon key, and composable content lambda.
2. Mutable current view state.
3. Toolbar actions that set selected view.

Reference:
- [ComponentsViewModel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/views/ComponentsViewModel.kt)
- [MainViewModel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/viewmodel/MainViewModel.kt)

## Pattern 3: Decorated window title bar composition

Use `DecoratedWindow` + `TitleBar` and compose:
1. Left side: navigation dropdown with icon + text row.
2. Center: window title.
3. Right side: action buttons (GitHub link, theme switch) with `Tooltip`.

Reference:
- [TitleBarView.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/view/TitleBarView.kt)

## Pattern 4: Settings/control blocks in vertical sections

Use a root `Column` with spaced sections:
1. Header visuals/title.
2. Theme selection chips in `FlowRow`.
3. Feature toggles with `CheckboxRow`.

Reference:
- [WelcomeView.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/view/WelcomeView.kt)

## Pattern 5: Side-by-side editor/preview panes

Use a `Row` with weighted panes:
1. Left editor pane.
2. Vertical divider.
3. Right preview pane.

Reference:
- [MarkdownView.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/view/MarkdownView.kt)

## Pattern 6: Composite controls row with Swing-backed actions

In standalone/desktop flows where file chooser is needed:
1. Obtain host component from `LocalComponent`.
2. Launch `JFileChooser` from button click.
3. Feed result into Compose state.

For deeper Compose↔Swing bridging (ComposePanel hosting, compositing flags, action-system bridges), use the `jewel-swing-interop` skill.

Reference:
- [MarkdownEditor.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/markdown/MarkdownEditor.kt)

## Practical guidance

1. Prefer spacing systems (`Arrangement.spacedBy`, padding constants) over ad-hoc per-item paddings.
2. Keep state ownership in viewmodels or dedicated state holders; keep composables mostly declarative.
3. Use Jewel typography (`JewelTheme.typography`) and colors (`JewelTheme.globalColors`) at section boundaries.
4. Keep accessibility semantics (`contentDescription`, traversal grouping) in container and icon/button layers.
