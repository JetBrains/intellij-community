# Structuring Jewel Code for Standalone/Bridge Portability

How to structure a Jewel surface so the *same* content composables run both in an IJPL plugin (Swing/LaF bridge) and
standalone (a Compose Desktop `main()`, previews, UI tests). This is the authoring counterpart to
`standalone-vs-bridge.md` (which covers picking the right theming source and reviewing for mismatches). Read this when
building a reusable surface, a shared UI module, or anything that must render in previews/tests without an IDE.

Verify the wrapper/provider APIs named here against the build you target; signatures evolve.

## Why portability is the default goal

Plugin UI is routinely run with **no IJPL LaF underneath** — `@Preview`s, UI unit tests, demo `main()`s, and shared
Compose modules. A surface that reads the LaF directly works in the IDE and breaks (or throws) everywhere else. The fix
is structural, not per-call: keep context-specific theming at the *edges* and the content composables context-agnostic.
The same goes for any direct and indirect access to IJPL APIs in the composition. Keep IJPL away from the composition,
move it to the presenter/controller layer, and when that is not possible, abstract it away behind a collaborator
interface that can be faked/subbed in standalone mode.

## The injection boundary (the core rule)

Split every surface into two layers:

- **Content layer** — the composables that draw the UI. They take theme values as parameters or from `JewelTheme`/a
  `CompositionLocal`, and **never** call `retrieveColor*`, `JBColor`, `UIManager`, `IslandsState`, `ExperimentalUI`, or
  any `com.intellij.*` API. Even a single `retrieveColorOrUnspecified("ColorPalette.Gray7")` inside reusable content is
  a bridge dependency: it belongs at the host/wiring edge, not in shared UI. This layer compiles and runs with no
  platform dependency.
- **Wiring layer** — small, context-specific entry points that resolve the theme and host the content. This is the
  *only* layer allowed to touch the bridge or platform.

If a content composable reaches the LaF directly, it is not portable — that is the finding `standalone-vs-bridge.md`
flags. This doc is how to avoid it by construction.

## Two wrappers, one content composable

Both Jewel theme entry points take `content: @Composable () -> Unit`, so the same content goes inside either:

- **Standalone:** `IntUiTheme(isDark = …) { MyContent() }` (`org.jetbrains.jewel.intui.standalone.theme.IntUiTheme`).
  Hosted by a Compose Desktop `application { … }`.
- **Bridge:** `SwingBridgeTheme { MyContent() }` (`org.jetbrains.jewel.bridge.theme.SwingBridgeTheme`). The usual host
  is `JewelComposePanel { MyContent() }`, which **already wraps its content in `SwingBridgeTheme` for you** — so do
  *not* nest another `SwingBridgeTheme` inside it. Use `SwingBridgeTheme` explicitly only inside
  `JewelComposeNoThemePanel { … }`/`composeWithoutTheme { … }`, the no-theme panel variants meant for applying the
  theme yourself. (`SwingBridgeTheme`, `JewelComposeNoThemePanel`, and `composeWithoutTheme` are marked experimental.)

So the portable shape is:

```kotlin
// shared module — no platform/bridge dependency
@Composable
fun MyContent(model: MyModel) { /* draws UI from JewelTheme + params */
}

// plugin module — JewelComposePanel already applies SwingBridgeTheme
JewelComposePanel { MyContent(model) }
// (or, if you must apply the theme yourself: JewelComposeNoThemePanel { SwingBridgeTheme { MyContent(model) } })

// standalone main()/preview/test
application { Window(…) { IntUiTheme(isDark = isSystemInDarkTheme()) { MyContent(model) } } }
```

`MyContent` is reused verbatim; only the wrapper differs per context. Alternatively, if you are writing an IJPL plugin,
you can have a single module with the bridge dependencies for prod code and the standalone dependencies for test code.
This is riskier because the prod bridge dependency can and will leak in the test code, and it can be used accidentally.

## Injecting values the theme can't supply

For values not covered by `JewelTheme` globals/component styles (custom roles, bespoke dimensions, app-specific colors),
don't read them inside the content. Inject a small **theming abstraction** instead:

- Define an interface (or a data holder) of the values the surface needs.
- Provide it via a `CompositionLocal` (or constructor parameter) at the wiring layer.
- Supply a **bridge implementation** (reads the LaF with layered fallbacks + light/dark split) and a **standalone
  implementation** (hardcodes the Int UI defaults — fine per `standalone-vs-bridge.md` rule 3). Select the impl at the
  entry point.

This is the "theming container" idea, but the container is *injected*, not called inline — so the content layer never
branches on context, and previews/tests just provide the standalone impl. Each getter still owns its layered fallback
(`primary key → palette fallback → light/dark default`) and applies it to colors, dp, insets, and corner sizes alike,
not just colors.

## Module/dependency hygiene

Structure makes portability enforceable:

- Keep the **content module free of `ide-laf-bridge` and `com.intellij.*` dependencies** so it physically cannot read
  the LaF and can compile/run standalone. The bridge wiring lives in the plugin module that depends on both.
- If one source set must target both, use the standalone impl as the default and inject the bridge impl only where the
  platform is present.
- A content module that pulls in the bridge dependency is a smell: it invites direct LaF reads and breaks standalone
  consumption. IJ Plugins are the exception, as mentioned earlier, but that is only a partial excuse as it has strong
  drawbacks in terms of leaks/footguns.

## Review hooks

When reviewing for portability (cite `standalone-vs-bridge.md` for the per-finding fix):

- A content composable calling `retrieveColor*`/`JBColor`/`UIManager`/`IslandsState`/`ExperimentalUI` directly →
  move the read to the wiring layer; inject the value. Do not require an obvious `SwingBridgeTheme` or `com.intellij.*`
  import before flagging this: a bridge helper import alone is enough when the composable is used in standalone demos,
  previews, or screenshot tests.
- A single theme wrapper hardcoded for one context (e.g., `SwingBridgeTheme` referenced from code meant to also run
  standalone) → parameterize the wrapper per entry point.
- The content module depending on `ide-laf-bridge` → likely a layering violation.
- A theming abstraction with only a bridge impl and no standalone impl → previews/tests can't run; add the hardcoded
  standalone impl.
