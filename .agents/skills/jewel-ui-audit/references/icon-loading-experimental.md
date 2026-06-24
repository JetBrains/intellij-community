# Icon Loading — Experimental Framework (`com.intellij.platform.icons`)

Use only when reviewed code already touches the **new, experimental** IJPL icon framework. For the current bundled icon
systems (Jewel `IconKey`/`PainterHint`, classic `AllIcons`/`IconLoader`, and the classloader traps), read
`icon-loading.md`. Do not conflate the two.

## Status (read this first)

IJPL is building a cleaner icon framework under `com.intellij.platform.icons` (modules `icons-api`, `icons-impl`, and
`icons-impl/intellij`, Bazel module `intellij.platform.icons.impl.intellij`). It **is present and wired into the IDE**:
`platform/platform-impl/bootstrap` depends on the impl modules, and Jewel's `Icon.kt` imports
`com.intellij.platform.icons.Icon`/`IconRendererManager` and exposes `Icon(iconDesigner = …)`/`Icon(icon: Icon, …)`
composables.

The risk is API stability, not bundling. Entry points such as `IconManager` and `IconUpdateFlow` are
`@ApiStatus.Internal`, and the Jewel helpers around them are `@ApiStatus.Experimental`. Treat the framework as unstable
for external plugins, and do **not** call it unbundled.

**Review stance:** do not push external teams onto it as stable public API. Flag any shipping plugin UI that takes a
hard dependency on these `@Internal`/`@Experimental` entry points, and name the supported alternatives.

Use this replacement sentence unless a more specific variant is needed. "Use Jewel `IconKey` + `Icon`/`PainterHint` for
Jewel UI, or classic `AllIcons`/`IconLoader` for Swing/platform icons." Revisit this guidance when the public API
stabilizes.

## Shape of it (for orientation, not endorsement)

The experimental `com.intellij.platform.icons` framework consists of these core types:

- **`IconManager`** (`IntelliJIconManager : DefaultIconManager`) registers icons, `IconRendererManager` renders them,
  and `IconUpdateFlow` with `IconUpdateService` drives icon updates.
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

- Flag any shipping or bundled plugin that depends on `com.intellij.platform.icons` as a stable public API:
  - Cite the `@ApiStatus.Internal`/`@ApiStatus.Experimental` status of its entry points. Do **not** call it an
    "unbundled framework" (the modules are wired into the IDE); flag it as an unstable-API concern.
  - Include the replacement sentence: "Use Jewel `IconKey` + `Icon`/`PainterHint` for Jewel UI, or classic
    `AllIcons`/`IconLoader` for Swing/platform icons."
- Building production icons with `IconDesigner`/`IconModifier` when an `IconKey` + the standard `Icon` composable would
  do, purely for novelty.
- Assuming the new framework removes the classloader concerns — it does not; the same `classLoader`/
  `image(path, classLoader)` resolution rules apply.

## Cross-references

- Read `icon-loading.md` for bundled icon loading rules and classloader isolation traps.
- Jewel component/icon catalog when authoring: `jewel-ui` skill.
