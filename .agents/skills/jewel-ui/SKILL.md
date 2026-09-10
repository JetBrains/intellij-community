---
name: jewel-ui
description: Build or modify Compose Desktop UI using JetBrains Jewel components, theming, and icon loading. Use when requests mention Jewel, IntUiTheme, SwingBridgeTheme, JewelTheme, ComponentStyling, DecoratedWindow, DefaultButton, Tabs, AllIconsKeys, IconKey, PainterHint, or building IntelliJ-styled Compose UI in standalone apps or IntelliJ Platform plugins. For embedding Compose into Swing (ComposePanel, ToolWindow tabs, compositing flags), use jewel-swing-interop.
---

# Jewel UI

Implement UI with Jewel by first selecting the runtime context, then applying the correct theme wrapper, then using Jewel components and icon APIs.

## Quick Snippets

Standalone:

```kotlin
IntUiTheme(isDark = false) {
    App()
}
```

IntelliJ plugin:

```kotlin
SwingBridgeTheme {
    App()
}
```

Icon loading:

```kotlin
object MyIcons {
    val Settings = PathIconKey("icons/settings.svg", MyIcons::class.java)
}

Icon(key = MyIcons.Settings, contentDescription = "Settings")
```

Decorated window (standalone, custom title bar):

```kotlin
IntUiTheme(
    theme = themeDefinition,
    styling = ComponentStyling.default().decoratedWindow(
        titleBarStyle = TitleBarStyle.light(),
    ),
) {
    DecoratedWindow(onCloseRequest = ::exitApplication) {
        TitleBar { /* title bar content */ }
        App()
    }
}
```

Customization of the title bar (colors, metrics, fullscreen-control handling) happens through `TitleBarStyle` passed to `ComponentStyling.default().decoratedWindow(...)` — not through `IntUiTheme(isDark = ...)` alone.

## Scope Boundary

This skill covers Jewel's theme wrappers, components, layout, icons, and typography — the Compose side of the UI. It does **not** cover Compose-in-Swing embedding mechanics. If the user's question is primarily about:

- Registering `ToolWindowFactory` / `ToolWindow` content
- Instantiating `ComposePanel` or wiring `setContent`
- `plugin.xml` tool window extensions
- `enableNewSwingCompositing` and related compositing/AWT flags
- `LocalComponent` and Swing ↔ Compose bidirectional integration

…the correct response is to name `jewel-swing-interop` explicitly as the skill for that work and stop. Do **not** walk through `ToolWindowFactory` / `ComposePanel` / `plugin.xml` setup here, even if you know how. You may still state that content inside the `ComposePanel` should use `SwingBridgeTheme` (a theming concern, in scope), and defer the rest to `jewel-swing-interop`.

## Classify Runtime Context

Decide runtime before writing code:

1. IntelliJ Platform plugin: use the Swing bridge (`SwingBridgeTheme`) and IntelliJ bundled modules.
2. Standalone Compose Desktop app: use `IntUiTheme` and standalone Jewel dependencies.

For agent-generated desktop UI prototypes, prefer a Jewel/desktop preview or sandbox when one is available. Choose Material-style mobile previews only deliberately for mobile/Android-targeting prototypes or when the user explicitly wants a mobile device-frame preview; Material styling is not the general desktop default.

If context is unclear, inspect build files and imports first:
- `org.jetbrains.jewel.bridge.theme.SwingBridgeTheme` implies plugin context.
- `org.jetbrains.jewel.intui.standalone.theme.IntUiTheme` implies standalone context.

Use [STANDALONE-VS-BRIDGE.md](references/STANDALONE-VS-BRIDGE.md) for dependency and wrapper snippets.

## Keep IntelliJ Platform APIs Out Of Composition

Do not leak or access IntelliJ Platform APIs (`IJPL`) directly or indirectly inside Jewel/Compose composition. Composables should render explicit state and emit events; IntelliJ Platform interaction belongs at the presenter, controller, service, or action boundary.

Avoid calling or indirectly reaching from composition into platform services, application state, registry, action system, project model, PSI, VFS, message bus, disposables, or other platform-owned mutable/thread-bound APIs. This protects recomposition stability, preview/test isolation, and compatibility across IntelliJ Platform updates.

Preferred pattern:

1. Platform boundary reads/writes IntelliJ state and owns threading/lifecycle concerns.
2. Boundary maps that state into stable UI state for the composable.
3. Composable renders the state and invokes callbacks.
4. Boundary handles callbacks and performs platform work outside composition.

If direct or indirect platform access from composition is absolutely unavoidable, use an injected collaborator passed as a parameter or composition-local-style dependency that can be replaced with a fake in tests. Keep the collaborator surface minimal, avoid platform objects in stable UI state, and be very deliberate about threading, read/write actions, disposables, and Compose stability impact.

## Apply Theming Correctly

Use the simplest valid theme API first:

1. Standalone quick start: `IntUiTheme(isDark = ...)`.
2. Standalone advanced: `IntUiTheme(theme = ..., styling = ..., swingCompatMode = ...)`.
3. IntelliJ plugin: `SwingBridgeTheme { ... }`.

When implementing custom standalone themes:

1. Build `ThemeDefinition` via `JewelTheme.lightThemeDefinition(...)` or `JewelTheme.darkThemeDefinition(...)`.
2. Override text styles using `JewelTheme.createDefaultTextStyle()` and `JewelTheme.createEditorTextStyle()`.
3. Pass composed styling through `ComponentStyling.default().with(...)` (or specialized style builders).
4. Prefer public API packages; avoid internal/experimental APIs unless explicitly required.

Use [THEMING.md](references/THEMING.md) for concrete patterns.
Use [THEMING-COLORS.md](references/THEMING-COLORS.md) for color-palette and semantic-color guidance.
Use [TYPOGRAPHY.md](references/TYPOGRAPHY.md) for text-style guidance and when-to-use rules.

### Key Theme-Derived Caches By Theme State

Do not key `remember` only by `JewelTheme.name`, a LaF name, or another human-readable label when the cached value is derived from theme data. Colours, metrics, typography, icon data, and Swing defaults can change while the visible theme name stays the same.

Prefer the narrowest correct key:

1. If the calculation is cheap, skip `remember`.
2. If the cached value depends on concrete values, key by those concrete values, such as colours, text styles, metrics, shortcut text, or strings.
3. If the cached value depends on broad Jewel theme or LaF environment state that is not practical to enumerate, key by `JewelTheme.instanceUuid`.

Use `JewelTheme.instanceUuid` as an invalidation token for theme-instance changes, not as a blanket replacement for precise keys. Avoid `JewelTheme.name` as a proxy for theme state.

## Pick the Right Component

Before writing component code, check [COMPONENT-SELECTION.md](references/COMPONENT-SELECTION.md) for the when-to-use rule. Jewel implements the JetBrains IntelliJ Platform UI Guidelines — those guidelines constrain which control fits a given interaction, not just which APIs exist.

Most common decisions:

1. Mutually exclusive pick from 2–4 options → `RadioButtonRow` under a `GroupHeader` (label ends with `:`). **Do not** use three `DefaultButton`s.
2. Multi-select of independent booleans → group of `CheckboxRow`. Use `ThreeStateCheckbox` for a "select all" parent.
3. Pick from 5+ options, long labels, or less-frequent setting → `ListComboBox` / `ComboBox`.
4. Primary action in a form or dialog → `DefaultButton`. Secondary / cancel → `OutlinedButton`. Never two `DefaultButton`s side by side.
5. Icon-only buttons → wrap in a `Tooltip` carrying the action name + keyboard shortcut. Every icon-only control needs one.
6. Text input → `TextField` for one-line; `TextArea` for multi-line / newline-valid content (commit messages, descriptions, code).

Use [COMPONENT-SELECTION.md](references/COMPONENT-SELECTION.md) for the full decision tables and [LABEL-RULES.md](references/LABEL-RULES.md) for label-writing conventions.

## Build UI With Jewel Components

Use Jewel components from `org.jetbrains.jewel.ui.component` for IntelliJ-styled UI.

1. Use Compose layout containers (`Row`, `Column`, `Box`) for composition.
2. Use Jewel controls (`DefaultButton`, `OutlinedButton`, `TextField`, `Checkbox`, `Tabs`, etc.) for interactive primitives.
3. Access colors and metrics through Jewel theme locals and component styling APIs instead of hardcoded values.

Use local samples as source-of-truth examples:
- [showcase sample directory](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/samples/showcase)
- [standalone sample directory](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel/samples/standalone)

Use [LAYOUT-PATTERNS.md](references/LAYOUT-PATTERNS.md) for composition archetypes extracted from those sample apps.
Use [COMPONENTS-CATALOG.md](references/COMPONENTS-CATALOG.md) for component-by-component catalog guidance.

### Keep Composables Small And Navigable

Treat composables like any other production code: keep them clean, clear, and as small as practical. Avoid huge composables with deep nesting and mixed responsibilities. Extract sub-composables when a branch, section, row, toolbar, footer, popup, or repeated layout block has a distinct purpose.

Organize files so readers can follow the UI top-down: put public entry-point composables near the top, then the private sub-composables they call in usage order. Keep helper functions, constants, and state-holder glue close to the code that uses them unless project conventions say otherwise.

Reusable composables are APIs. Give public or cross-file reusable composables KDoc that states their purpose, key parameters, slot expectations, and any non-obvious behavior. For high-level API design, use the AndroidX Compose guidance as a reference, adapted for Desktop/Jewel rather than Android-only APIs:

- [Compose API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-api-guidelines.md)
- [Compose component API guidelines](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/compose/docs/compose-component-api-guidelines.md)

Do not copy those guidelines wholesale into this skill; link to the source so the guidance stays current.

## Load Icons The Jewel Way

Use `IconKey`-based loading for portability across standalone and bridge:

1. Use `PathIconKey(path, iconClass)` when icon path is the same across old/new UI.
2. Use `IntelliJIconKey(oldUiPath, newUiPath, iconClass)` when paths differ.
3. Use `Icon(key = ..., contentDescription = ...)` or `Image(iconKey = ...)` instead of deprecated raw `painterResource`.
4. Use `AllIconsKeys` for IntelliJ platform icons.

When using `AllIconsKeys` in standalone apps, ensure IntelliJ icons are on classpath (recommended: `com.jetbrains.intellij.platform:icons`).

Use `PainterHint` only when stateful/dynamic path or runtime icon patching behavior is required.

When debugging icon-not-found errors, confirm runtime context (standalone vs plugin) before recommending a fix — `AllIconsKeys` availability and classpath layout differ between the two. Ask the user if context is not clear from the question.

Use [ICONS.md](references/ICONS.md) for icon patterns and pitfalls.

## Source Permalinks

When citing source in responses, prefer `master` links for always-latest behavior:

- [README.md (standalone, bridge, icons)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md)
- [standalone sample main](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/Main.kt)
- [Icon API (`Icon` composable)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component/Icon.kt)
- [Icon key types](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/icon/IconKey.kt)
- [IntUiTheme implementation](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/theme/IntUiTheme.kt)

## Version Discipline

Treat this skill as Jewel-version scoped.

1. When version matters, check build files (`libs.versions.toml`, `build.gradle.kts`) first; ask only if ambiguous.
2. Validate compatibility against [Jewel release notes](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/RELEASE%20NOTES.md).
3. Prefer APIs available in the target version; avoid suggesting newer APIs without stating the minimum version.
4. If publishing a version-pinned variant of this skill, replace `master` links with release-tag links.

## Implementation Checklist

Before finishing:

1. Confirm context-specific theme wrapper is correct (`IntUiTheme` vs `SwingBridgeTheme`).
2. Confirm icon code uses `IconKey` and classpath-friendly resource resolution.
3. Confirm dependencies match context.
4. Confirm no unnecessary Material dependency is introduced in standalone flows.
5. Confirm code compiles with current module and imports.
6. Confirm composables do not directly or indirectly access IntelliJ Platform APIs; platform interaction stays at the presenter/controller boundary or behind a tiny injected fakeable collaborator.
7. Confirm large or deeply nested composables are split into clear sub-composables, file order follows top-down usage, and reusable composables have useful KDoc.
8. Confirm theme-derived `remember` values are either uncached, keyed by the concrete values they use, or keyed by `JewelTheme.instanceUuid` when broad theme/LaF state is the dependency; do not key them by `JewelTheme.name` alone.
9. For standalone apps, confirm JetBrains Runtime is used (Jewel requires JBR for full font/rendering behavior).