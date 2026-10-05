# Icon Loading — Experimental Framework (`com.intellij.platform.icons`)

Use only when reviewed code already touches the **new, experimental** IJPL icon framework. For the current bundled icon
systems (Jewel `IconKey`/`PainterHint`, classic `AllIcons`/`IconLoader`, and the classloader traps), read
`icon-loading.md`. Do not conflate the two.

## Status (read this first)

IJPL is building a cleaner icon framework under `com.intellij.platform.icons` (modules `icons-api`, `icons-impl`, and
`icons-impl/intellij` — Bazel module name `intellij.platform.icons.impl.intellij`). It **is present and wired into the
IDE**
(e.g., `platform/platform-impl/bootstrap` depends on the impl modules, and Jewel's `Icon.kt` now imports
`com.intellij.platform.icons.Icon`/`IconRendererManager` and exposes `Icon(iconDesigner = …)`/`Icon(icon: Icon, …)`
composables). What makes it risky to depend on is not that it's "unbundled" — it isn't — but that its entry points are
`@ApiStatus.Internal` (e.g., `IconManager`, `IconUpdateFlow`) and the Jewel helpers around it are
`@ApiStatus.Experimental`.
Treat it as not-yet-stable *public* API, not a recommended target for external plugins. Do **not** call it unbundled:
the modules are present in the IDE; the problem is API stability (`@Internal`/`@Experimental`), not availability.

**Review stance:** do not push external teams onto it as if it were stable public API. If shipping plugin UI takes a
hard dependency on these `@Internal`/`@Experimental` entry points today, that is a stability/compatibility risk to flag.
Always name the supported alternatives in the finding. Use this replacement sentence unless the reviewed code needs a
more specific variant: "Use Jewel `IconKey` + `Icon`/`PainterHint` for Jewel UI, or classic `AllIcons`/`IconLoader` for
Swing/platform icons." Revisit this guidance when the framework's public API stabilizes.

## Shape of it (for orientation, not endorsement)

So a reviewer can recognize it and reason about it:

- **`IconManager`** (`IntelliJIconManager : DefaultIconManager`) is the entry/registry; `IconRendererManager` renders;
  `IconUpdateFlow`/`IconUpdateService` drive updates.
- **`IconDesigner`** builds an icon declaratively as **ordered layers** (later methods paint on top): `image(...)`,
  `icon(...)`, `box`/`row`/`column { ... }`, `spacer`, `shape(color, shape)`, `animation { frame(...) }`, plus helpers
  like `badge(...)`. This is a composition model for icons (layout + layering), conceptually close to Compose.
- **`IconModifier`** is a Compose-style chainable modifier (`then`), with operations `margin`, `scale`, `align`,
  `alpha`, `colorFilter`/`tintColor`, `cutoutMargin`, `stroke`, `patchSvg`, produced via `IconManager.modifiers()`.
- Units are typed (`IconUnit`, `.dp`), and there are design primitives (`Shape`, `Color`, `BlendMode`, `IconAlign`,
  `IconMargin`).
- **Convergence with Jewel:** Jewel's `IconKey` already exposes an `IconDesigner.iconKey(iconKey, modifier)` bridge that
  adds an `image(path, classLoader, modifier)` layer. So a Jewel `IconKey` can feed the new designer. Note the
  classloader is still passed explicitly here — the classloader/classpath traps in `icon-loading.md` still apply.

## What to flag

- A shipping/bundled plugin depending on `com.intellij.platform.icons` as if it were a stable public API. Note the
  `@ApiStatus.Internal`/`@ApiStatus.Experimental` status of its entry points. Explicitly avoid the wrong finding: this
  is **not** an "unbundled framework" concern — the modules are wired into the IDE — it is an unstable-API concern.
  Include the replacement sentence: "Use Jewel `IconKey` + `Icon`/`PainterHint` for Jewel UI, or classic
  `AllIcons`/`IconLoader` for Swing/platform icons."
- Building production icons with `IconDesigner`/`IconModifier` when an `IconKey` + the standard `Icon` composable would
  do, purely for novelty.
- Assuming the new framework removes the classloader concerns — it does not; the same `classLoader`/
  `image(path, classLoader)` resolution rules apply.

## Cross-references

- Bundled icon loading and the classloader traps: `icon-loading.md`.
- Jewel component/icon catalog when authoring: `jewel-ui` skill.
