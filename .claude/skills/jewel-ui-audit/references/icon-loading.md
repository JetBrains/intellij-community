# Icon Loading (Jewel + existing IJPL machinery)

Use when reviewing how Compose/Jewel UI in an IntelliJ-based IDE loads and renders icons with the **current, bundled**
systems. This reference maps the contract, classloader traps, and failure modes from the Jewel and IJPL sources. Confirm
symbol names against local sources before proposing code.

For the separate, experimental `com.intellij.platform.icons` framework, read `icon-loading-experimental.md` instead —
do not conflate the two.

## The Jewel icon contract

- Icons are identified by an **`IconKey`** (`org.jetbrains.jewel.ui.icon.IconKey`), not a raw path. Two implementations:
  - `PathIconKey(path, iconClass)` — one path for both UI generations.
  - `IntelliJIconKey(oldUiPath, newUiPath, iconClass)` — **separate old-UI and New-UI paths**; `path(isNewUi)` picks.
    This exists because New UI remapped many icon paths. Using a single path for an icon that has a New-UI variant is
    a finding: it will show the wrong (old) icon under New UI.
- `iconClass` matters: it provides the `ClassLoader` the resource is resolved against. A wrong/defaulted `iconClass` is
  the usual cause of "icon not found" for plugin-owned icons — the resource must be loadable from that class's loader.
- The generated **`AllIconsKeys`** exposes platform icons as `IconKey`s. In a plugin, prefer these (or your own
  generated keys) over hand-built paths so New-UI mapping, theming, and dark variants are handled.
- Rendering goes `IconKey` → `PainterProvider` (`rememberResourcePainterProvider`/`bridgePainterProvider`) →
  `Painter`, and the `Icon` composable ties it together. Do not hand-roll `Image(painterResource(...))` for IDE icons;
  you lose the hint pipeline below.

## Two distinct render paths — be precise

Jewel and Swing/IJPL load icons through **separate pipelines**. They are not one delegating to the other, and conflating
them produces wrong review advice.

- **Swing/IJPL path**: `IconLoader` and `AllIcons` produce a `CachedImageIcon` for platform Swing rendering and any
  `javax.swing.Icon`. This pipeline applies dark-variant selection, New-UI path mapping, SVG color patching, DPI
  scaling, deferred icons, and icon patchers or overlays.
- **Jewel path**: `ResourcePainterProvider` resolves an `IconKey` into a `Painter` that the `Icon` composable renders:
  - Jewel **reimplements** icon loading with its own `ClassLoader` set, resource reads, SVG parsing
    (`DocumentBuilderFactory` + Compose `decodeToSvgPainter`/`decodeToImageVector`), painter cache, and `PainterHint`
    pipeline.
  - On failure, Jewel does **not** throw or fall back to a platform icon; it logs and substitutes a **magenta
    `ColorPainter`** as a visual diagnostic.
- **The bridge feeds data, not rendering.** In the IDE, Jewel does not render through `IconLoader`.
  - `BridgePainterHintsProvider` (a `PalettePainterHintsProvider`) reads New-UI icon path mappings and the theme color
    palette (`UITheme.getColorPalette()`), exposing them as `PainterHint`s for `ResourcePainterProvider`.
  - `IntelliJIconKey.fromPlatformIcon(swingIcon)` extracts only the path strings (`originalPath`/`expUIPath`) from a
    platform icon; it never renders via `IconLoader`.

Review consequences of the split:

- **Platform-only icon features do not automatically apply to Jewel icons.** Deferred/dynamic icons and `IconLoader`
  patchers belong to the Swing path. A Jewel `Icon` rendered from an `IconKey` gets only what the `PainterHint` pipeline
  provides (path mapping and palette recolor). Do not assume an effect seen on a Swing icon carries over to Jewel.
- **A magenta icon is a Jewel-load failure**, meaning `ResourcePainterProvider` could not load or parse the resource
  (wrong path, missing from its classloaders, or malformed SVG). Contrast this with the Swing path when useful: classic
  Swing icon misses often render blank or silent rather than magenta.
- **Two caches, two classloader handlings.** Jewel's `ResourcePainterProvider` holds its own classloader set and cache,
  independent of `IconLoader`'s `Pair<String, ClassLoader?>` cache. The classloader traps below apply to **both**, but
  they are resolved by different code; a fix in one path does not fix the other.
- **SVG color patching differs.** Both recolor SVGs by theme, but Jewel does it via its `PainterSvgPatchHint`/palette
  hints on its own parsed DOM, not via the platform SVG patcher. Custom-painted or non-standard SVGs can recolor
  differently (or not) between a Swing render and a Jewel render of the same asset.

### PainterHint — why you don't just load a path

`PainterHint` (sealed; custom impls disallowed) is how Jewel patches icon loading. Its subtypes fall into two families
you'll care about for review (there are more — e.g., `PainterWrapperHint`, and marker interfaces `SvgPainterHint`/
`BitmapPainterHint`/`XmlPainterHint`):

- `PainterPathHint` — rewrites the resource path (New-UI path mapping, dark-theme `_dark` variants, size variants).
- `PainterSvgPatchHint` — rewrites SVG contents (palette/stroke color substitution so icons recolor with the theme).

### PainterHint.Stroke vs. ColorFilter.tint (icon recoloring vs. layer tinting)

Jewel and Compose provide two different mechanisms for changing icon colors, operating at different phases:

1. **`PainterHint.Stroke(color)` (Load-Time SVG DOM Patching)**:
   - Operates during resource loading and SVG DOM parsing before the Compose `Painter` is constructed.
   - Replaces background fill colors (`#EBECF0`, etc.) with `Color.Transparent` (stripping fills) and replaces
     stroke colors with `color`.
   - Built-in components like `IconActionButton` and `SelectableIconActionButton` pass `Stroke(color)` as a
     `PainterHint` under the hood.
2. **`ColorFilter.tint(color)` (Draw-Time Compose Layer Filter)**:
   - Operates during Compose's drawing phase using graphic layer blend modes (`BlendMode.SrcIn` / `SrcAtop`).
   - Paints *every non-transparent pixel* in the rendered painter with `color`, forcing fill alpha and interior
     details to full opacity solid colors.

**Rules for Icon Tinting**:

- **Monochrome / Stroke Icons**: Use `PainterHint.Stroke(color)` for stroke-aware recoloring instead of Compose
  `ColorFilter.tint()`.
- **Filled / Multi-Color / Gradient Icons**: Do NOT apply Compose layer-level `ColorFilter.tint()` to filled icons.
  Layer tinting clobbers fill alpha channels and interior shapes, making filled icons look distorted or broken.
- **Never Mix `Stroke` Hint and Layer `ColorFilter`**: Do not pass `ColorFilter.tint()` to an `Icon` that already uses
  `PainterHint.Stroke(color)`. The layer filter re-tints the entire drawn icon at draw time and overrides the `Stroke`
  hint's SVG DOM recoloring.

Consequences for review:

- Theme/dark handling and New-UI path selection happen **inside** this pipeline. Code that bypasses it (loads a fixed
  `.svg` directly, or hardcodes a `_dark` path) loses automatic dark-variant selection and theme recoloring. Flag direct
  path loading of IDE icons.
- SVG icons get theme-aware color patching; raster icons (PNG) do not (`BitmapPainterHint` vs `SvgPainterHint`). Prefer
  SVG for themable IDE icons; a PNG that needs to recolor per theme is a finding.

## Standalone vs bridge for icons

- **Standalone Jewel**: icons resolve from resources via the standalone painter-hints provider and the int-ui palette.
  There is no platform `IconLoader`.
- **Bridge/plugin**: Jewel bridges the IDE icon system. `IntelliJIconKey.fromPlatformIcon(swingIcon)` converts an
  `AllIcons` Swing icon into a Jewel `IconKey`, but only works for resource-backed icons implementing
  `IconPathProvider` (it reads `originalPath`/`expUIPath`). Converting an arbitrary runtime/synthetic `javax.swing.Icon`
  will fail the `check`; flag attempts to bridge non-resource icons.
- As with color, do not assume the platform icon system exists when the same UI may run standalone (UI tests, previews).
  Resolve icons through the `IconKey` abstraction, not a hard platform dependency in shared code.

## Classic Swing/IJPL icon machinery

This is the **Swing path**, separate from Jewel's render path (see "Two distinct render paths").
`com.intellij.openapi.util.IconLoader` + the generated `com.intellij.icons.AllIcons` are the classic system used by
Swing UI:

- `AllIcons` icons are typically `CachedImageIcon`s carrying `originalPath` and an `expUIPath` (the New-UI remap). When
  using the `IntelliJIconKey.fromPlatformIcon` it reads these *path strings* to build a Jewel `IconKey`, but rendering
  still happens in Jewel's provider, not here.
- `IconLoader` applies dark-variant selection, New-UI path mapping, SVG color patching, scaling/DPI, deferred icons, and
  patchers/overlays. Jewel's `PainterHint` pipeline is a **separate reimplementation** of only the path-mapping and
  recolor subset, not a call into `IconLoader`. Use the public APIs instead of hand-rolling either pipeline.
- Icons are lazy/cached and can be *deferred*, resolving differently after a theme change. Holding a one-time `Painter`
  or bitmap from an IDE icon across theme switches causes staleness, matching the frozen-`JBColor` bug in
  `ijpl-theming-and-editor-scheme.md`. On the Jewel path, the analogous bug is not re-resolving a `PainterProvider` or
  `Painter` across theme changes.

## Icon filename variants (`@2x`, `_dark`, `_stroke`, `@WxH`)

You ship **one** base path (e.g., `/icons/foo.svg`) and the loader resolves sibling files by appending suffixes; you do
not reference the variants directly. The variant filenames are built by a literal `base + suffix + "." + ext`
transform, and the loader tries a candidate list in priority order (Swing path: `ImageDescriptor`/
`createImageDescriptorList` in `com.intellij.ui.icons`). The suffixes, from source:

- **`_dark`** — dark-theme variant (`foo_dark.svg`). Applies to both SVG and raster.
- **`@2x`** — HiDPI/retina variant, triggered when the pixel scale ≠ 1:
  - **Raster (`foo@2x.png`)**: a bitmap at twice the pixel dimensions (32×32 PNG for a 16×16 icon), tagged with density
    `2f` to draw crisp into logical 16×16 space on HiDPI screens.
  - **SVG on the Swing path**: `foo@2x.svg` is also checked intentionally as a `data-scaled` normalization hint.
  - **SVG on the Jewel path**: Jewel does **not** use `@2x` for SVG at all (see below); do not assume `foo@2x.svg` is
    picked up there.
- **Combined dark + HiDPI** — the loader checks three dark candidates (`foo@2x_dark`, `foo_dark@2x`, and `foo_dark`)
  before non-dark names:
  - **HiDPI screens** try `@2x` first: `foo@2x_dark` → `foo_dark@2x` → `foo_dark`.
  - **Non-HiDPI screens** try the reverse: `foo_dark` → `foo_dark@2x` → `foo@2x_dark`.
  - Ship dark variants as `foo_dark.svg` (plus `foo@2x_dark.png` for raster dark HiDPI).
- **`_stroke`** — the monochrome "stroke" variant used in selected/highlighted contexts (`foo_stroke.svg`), tried first
  when a stroke icon is requested. Note: there is **no** `_dark_stroke`/`_stroke_dark` convention in the loader — don't
  invent one.
- **`@WxH` (e.g., `@20x20`)** — selects a *size-specialized* variant file (such as New UI `toolWindowFoo@20x20.svg` vs.
  16×16 `toolWindowFoo.svg`).
  - An ordinary SVG's intrinsic size comes from its `width`/`height` attributes (defaulting to 16×16), not from an
    `@WxH` suffix. Size normal SVGs via `width`/`height`, not `@20x20`.

Review implications:

- **Reference the base name only**, and ship the variant files beside it with the exact suffix spelling above. A
  component that hardcodes `foo_dark.svg` (or `foo@2x.svg`) directly defeats the loader's automatic selection — flag it
  (same finding class as bypassing the `PainterHint` pipeline).
- **The suffix order is `@2x` then `_dark` in the combined form** (`foo@2x_dark`), which is preferred over
  `foo_dark@2x`. Although `foo_dark@2x` is still found second in the candidate list, the canonical spelling in the SDK
  docs is `iconName@2x_dark.svg`.
- On the **Jewel** side, suffix logic lives in `PainterSuffixHint` subclasses (`Dark` → `_dark`, `HiDpi` → `@2x`,
  `Stroke` → `_stroke`, `Size` → `@WxH` in `org.jetbrains.jewel.ui.painter.hints`, wired in
  `StandalonePainterHintsProvider.hints()`), not in `IconLoader`:
  - **Jewel does not apply `@2x` to SVGs.** `HiDpi()` applies only to bitmaps (`my-icon.svg → N/A`; the platform uses
    `Size` for SVGs and `HiDpi` for PNGs).
  - Jewel scales SVGs via the `Size` hint (`my-icon@20x20.svg`) or intrinsic `width`/`height`, and ignores
    `my-icon@2x.svg` (unlike the Swing loader). Ship `_dark` variants for both paths, `@2x` only for PNG, and intrinsic
    size or `@WxH` for SVG.

These are the Swing loader's rules, reconstructed from `ImageDescriptor.kt`/`imageCache.kt`/`customIconUtil.kt`; the
public SDK docs at <https://plugins.jetbrains.com/docs/intellij/icons.html#icon-files> cover the `_dark`/`@2x` and New
UI `@20x20` filename tables but not the full candidate-ordering. Treat exact ordering as code-level detail that can
shift between versions; the *suffix spellings* are the stable, shippable contract.

## Stateful icons and how specialized variants fall back

Many controls render from a **single base icon** plus *state* variants selected by the component's interaction state —
the classic examples are checkbox and radio-button SVGs. On the **Jewel** path these are filename suffixes chosen by
hints, and they compose with the `_dark`/`@2x`/`@WxH` variants above:

- **`Selected(...)`** → appends `Selected` (`radioSelected.svg`).
- **`Stateful(state)`** → appends exactly one of `Focused`/`Pressed`/`Hovered` when enabled, or `Disabled` when not
  enabled (`radioDisabled.svg`, `radioFocused.svg`). At-rest enabled adds nothing as it is the base case.
- These stack: a checkbox actually requests e.g., `Selected(...) + Stateful(...)` together (see `Checkbox.kt`/
  `RadioButton.kt`), producing composite names like `checkBoxSelectedFocused.svg`, and still combine with `_dark`/size,
  e.g., `checkBoxSelectedFocused_dark.svg` or `myIconSelected@20x20.svg`.

**Fallback mechanism for missing icon variants:** Jewel's `ResourcePainterProvider` applies each `PainterPathHint` as a
branch (`scopes = scopes.flatMap { listOfNotNull(it.apply(hint), it) }`), expanding the candidate list into paths with
and without the suffix. It loads the first candidate found on the classpath (`firstNotNullOfOrNull {
resolveResource(it) }`), falling through from the most-specific composite name down to the bare base icon:

- You do **not** need to ship every state×theme×size combination. Ship the base (`checkBox.svg`), add only the variants
  that actually differ (`checkBoxSelected.svg`, `checkBoxDisabled.svg`, `_dark` versions), and the rest degrade to base
  automatically. A missing `Hovered` variant just renders the at-rest icon on hover — not an error.
- Hard failure occurs only when **no** candidate in the branch (including the base) resolves. The provider logs
  `Resource '…' not found` and returns the **magenta error painter**. State this in reviews: missing specialized
  variants degrade to base, while a missing base renders magenta in Jewel (whereas Swing often fails blank or silent).
- Live component state drives state and selection hints, so the resolved painter updates as the user interacts.
  Capturing a provider or `Painter` once outside the keyed `getPainter`/`remember` freezes a stale state variant.

**Swing side:** `Selected`/`Focused`/`Pressed`/`Hovered`/`Disabled` filename suffixes are Jewel-only
(`Selected`/`Stateful` in `org.jetbrains.jewel.ui.painter.hints`; branch-and-fall-back in
`ResourcePainterProvider.loadPainter`). Classic `ImageDescriptor`/`IconLoader` covers only
`_dark`/`@2x`/`_stroke`/`@WxH`; Swing stateful control icons come from `UIManager`/LaF icon keys. When porting a
stateful Swing control to Jewel, do not assume `fooSelected.svg` exists just because the Swing control had a selected
state.

## Two different SVG renderers (Swing vs Skia)

The Swing and Jewel render paths use **different SVG engines**, which can cause the same `.svg` asset to render
correctly in Swing while appearing wrong or blank in Jewel (or vice versa).

- **Swing/IJPL** rasterizes SVG with **JSVG** (JetBrains' use of `com.github.weisj.jsvg`, a pure-Java SVG renderer; see
  `com.intellij.ui.svg`).
- **Jewel** (Compose Multiplatform → Skiko) decodes SVG via CMP's `decodeToSvgPainter`, which is backed by **Skia's**
  SVG support (accessed over JNI in `org.jetbrains.skia.svg`).

These are independent implementations with **different SVG feature coverage and rendering quirks**. The practical
consequences for review:

- An icon authored/verified against one engine is **not guaranteed** to render identically (or at all) on the other.
  Effects, filters, gradients, masks, text, and less-common SVG features are the usual divergence points — but exactly
  which features differ is a moving target across versions of JSVG and Skia.
- **Do not trust a hardcoded capability matrix here.** When an icon must render in both Swing and Jewel surfaces,
  **verify the asset in both** instead of assuming parity. Rendering in the IDE's Swing UI does not prove it renders
  through Jewel/Skia, and vice versa.
- Keep IDE icon SVGs to the simple, conventional subset the platform's own icons use (basic shapes/paths, palette-driven
  fills). Unusual SVG features are the most likely to diverge between engines. Flag richly-featured SVGs used in Jewel
  surfaces as needing explicit Skia-render verification.
- Jewel can also load Android-style XML vector drawables via `decodeToImageVector` (the `.xml` path) — a format the
  Swing path does not use. An asset relying on one format does not transparently work on the other path.

Treat this as a known-divergence flag rather than a fixed spec: two engines exist, so test the asset on every engine it
targets.

## Caching and icon lifecycle (the two cache models differ sharply)

The Swing/IJPL and Jewel paths cache very differently. Getting this wrong shows up as stale icons, surprising memory
cost, or repeated decode work. Grounded in the current sources:

**Swing/IJPL — global + persistent, multi-tier:**

- **Global icon cache**: `IconLoader` maintains a process-wide Caffeine cache mapping `Pair<String, ClassLoader?>` to
  `CachedImageIcon` with `maximumSize(256)` and `expireAfterAccess(30 minutes)`:
  - On theme/transform changes, `CachedImageIcon` instances stay in the cache and re-resolve rasterization on the fly.
    `clearCache()` bumps the global transform mod-count and runs cleaners to clear downstream image/IO caches rather
    than flushing `iconCache` (which clears only in `clearCacheInTests` or `detachClassLoader`).
- **In-memory rasterized-image cache**: `com.intellij.ui.icons.imageCache` uses a Caffeine cache mapping
  `CacheKey(path, scale, digest)` to `BufferedImage` with `expireAfterAccess(30 seconds)`.
- A **persistent on-disk cache** of rasterized SVGs: `SvgCacheManager`, backed by an **H2 MVStore** at
  `<system>/icon-cache-v2.db`. Rasterized SVG output **survives across IDE restarts**, keyed by content/scale. There is
  also an `ioMissCache` to avoid retrying known-missing resources.
- Net: an `AllIcons`/`IconLoader` icon is cheap to ask for repeatedly — it is shared globally and its rasterization is
  memoized in memory and on disk.

**Jewel — in-memory, provider-local only (no disk, no global):** The cache chain spans `intellij-community` Jewel →
`compose-multiplatform` resources → `skiko` → Skia. Review against the current CMP **resources** library
(`org.jetbrains.compose.resources`); the older `androidx.compose.ui.res.SVGPainter`/`loadSvgPainter` in
`compose-multiplatform-core` are **deprecated**. Each level caches as follows:

- **Parse (`SVGDOM`): cached once.** On a cache miss, `ResourcePainterProvider` calls CMP `decodeToSvgPainter` and
  stores the `Painter` in its `ConcurrentHashMap<Int, Painter>` (keyed by accepted-hints hash * 31 + density hash).
  `decodeToSvgPainter` parses bytes into a Skiko `SVGDOM` once inside `SvgPainter`, reusing it for that painter's life.
- **Rasterize-to-bitmap: per-draw in current source.** `SvgPainter` holds a `DrawCache` intended to rasterize `SVGDOM`
  into an `ImageBitmap` once ("3x-4x higher FPS"):
  - In `SvgPainter.kt` and `DrawCache.kt`, the guard `if (previousDrawSize != size)` in `onDraw` checks a
    `previousDrawSize` field that is **never assigned**, so `drawCache.drawCachedImage { drawSvg(size) }` re-runs
    `SVGDOM.render` on every draw.
  - This frame-to-frame memoization bug is tracked as [CMP-10433](https://youtrack.jetbrains.com/issue/CMP-10433);
    re-check the source across CMP versions.
- **Skiko/Skia: no SVGDOM raster memoization.** `SVGDOM.render(canvas)` delegates straight to native `SkSVGDOM::render`
  and re-traverses/re-rasterizes the vector tree each call; Skia's resource/GPU caches do not memoize a whole SVGDOM's
  output. Cheap repeat draws require an explicit offscreen `Surface` + `makeImageSnapshot()` — which is exactly what
  `DrawCache` is *trying* to be.
- **Provider-local caching only**: `ResourcePainterProvider` stores painters in its own map without a global cache, disk
  cache, or cross-provider sharing. Because `rememberResourcePainterProvider(...)` keys the provider on `iconKey`,
  `classLoader`, and `isNewUi`, the cache lives only as long as the composition; Jewel never calls `SvgCacheManager` or
  `IconLoader` (`platform/jewel` only uses `patchIconPath` for path mapping).

Review consequences:

- **Don't recreate providers on the hot path.** Constructing a `ResourcePainterProvider` ad hoc (instead of
  `rememberResourcePainterProvider`) inside a lazy-list item or frequently-recomposed scope discards the per-instance
  parse cache every recomposition, forcing a re-parse (new `SVGDOM`) each time. Flag provider construction not behind
  `remember`.
- **Jewel does not benefit from the platform's disk/global icon cache.** On a Jewel surface, an asset is parsed once per
  provider and (under CMP-10433) re-rasterized per draw without `SvgCacheManager`/`IconLoader` memoization. Do not
  assume platform icon caching speeds up the Jewel render path.
- **Invalidation is asymmetric:**
  - **Swing**: `CachedImageIcon`s adapt to theme/transform changes in place via transform mod-counts and image/IO cache
    cleaners, while the disk cache is content-keyed.
  - **Jewel**: provider caches drop when composition leaves, and theme changes re-run the keyed `remember` to re-resolve
    hints. Capturing a provider or `Painter` outside the keyed `remember` serves stale icons across theme switches.
- **Memory shape differs.** Swing caps the icon cache (256, plus a 32MB weighted image cache) and expires entries;
  Jewel's per-provider maps are unbounded but die with the composition. Long-lived composables resolving many distinct
  icons through one provider hold all those painters for the provider's lifetime.

## Classloader and classpath traps (the "works from sources, broken in release" class)

This is the highest-value, easiest-to-miss part. Icons are bound to a **`ClassLoader`**, and the binding behaves
differently in a dev/sources run than in a release build.

- **The icon cache key is `Pair<String, ClassLoader?>`.** The same path under two classloaders produces two distinct
  cache entries. `IconLoader.getIcon`/`findIcon` takes a `Class<*>` (`aClass.classLoader`) or an explicit `ClassLoader`
  (the deprecated no-arg overload infers the caller class reflectively), making the classloader part of the icon's
  identity.
- **Resource resolution uses that classloader.** A `CachedImageIcon` is backed by a loader that resolves the SVG/PNG
  resource from its bound classloader (`isMyClassLoader`, `detachClassLoader`). If the resource is not on that
  classloader's classpath, the icon does not load.
- **Reflective icon paths cross-load a class.** A path like `AllIcons.General.Add` (a "reflective" path: contains
  `Icons.`, not `/`-prefixed, not `.svg`) is resolved by `getReflectiveIcon`, which does
  `classLoader.loadClass(className)` then reads the static `Icon` field. So referencing **another plugin's** icon-holder
  class (or even a platform one) requires that class to be loadable from the classloader you passed.
- **Why it works from sources but not in release:**
  - In a dev/sources run, the flat module classpath often lets any classloader load another plugin's icon class or
    resource.
  - In a packaged build, each plugin has an **isolated classloader**; loading another plugin's icon class or resource
    from your classloader fails and the icon silently disappears.

What to require:

- Pass the **correct owning class/classloader** for the icon's resource — the class from the module that actually ships
  the icon resource, not a convenient nearby class.
- **Do not reference another plugin's icons by reflective path/class** unless there is a hard dependency that guarantees
  the class is loadable from your classloader at runtime. Cross-plugin icon reuse is the classic source of release-only
  breakage. Prefer the depended-on plugin exposing its icons through API, or ship your own copy.
- In Jewel, the `iconClass` on an `IconKey` is exactly this classloader source — a wrong/defaulted `iconClass`
  reproduces the same failure. Verify it points at the resource-owning module.
- Treat "the icon shows in the IDE-from-sources run" as **not** sufficient evidence it works shipped; call out
  cross-classloader icon references as needing a packaged-build check.

### Getting an icon for a `PsiFile`/`VirtualFile` you don't own (the cross-plugin case)

A common release-only failure occurs when a plugin needs the icon for a foreign file type (such as a `KtFile`,
Gradle/Groovy file, or framework config) without depending on the owning plugin.

Do not reference the owning plugin's icon constants (`KotlinIcons.FILE`, its `AllIcons`-style holder, or a reflective
`...Icons.` path). Those classes live on the other plugin's isolated classloader and fail in packaged builds.

The correct approach is to let the platform's **extension-point-based** icon resolution do it, so the owning plugin
supplies its own icon through the platform without you classloading anything from it:

- **From a `PsiElement`/`PsiFile`:** call `element.getIcon(flags)` (the `Iconable.getIcon` contract, flags from
  `Iconable.ICON_FLAG_*`), or `PsiIconUtil.getIconFromProviders(element, flags)` to go straight through the registered
  `IconProvider` extension points. The Kotlin plugin's own `IconProvider` answers for a `KtFile`; you never touch its
  classes.
- **From a `VirtualFile`:** call `IconUtil.getIcon(file, flags, project)` (deferred) or
  `IconUtil.computeFileIcon(file, flags, project)` (non-deferred, safe on background threads). These route through
  `FileIconProvider`/`FileIconPatcher` extensions and the file type's own icon — again owned by whichever plugin
  registers them.
- The resulting `Icon` is a Swing icon whose resource is already bound to the **owning** plugin's classloader by that
  plugin, so it renders correctly *as a Swing icon* when shipped.

So the rule: for files/PSI you don't own, resolve the icon **by asking the platform about the object**, never by
naming the other plugin's icon. On a Swing surface that is the whole story — resolution happens on the owner's side and
you're done.

**On a Jewel surface**, converting that platform Swing icon into a Jewel `IconKey` (via
`IntelliJIconKey.fromPlatformIcon`) hits two hurdles:

- **The icon is rarely a direct `IconPathProvider`.** `IntelliJIconKey.fromPlatformIcon` `check`s for
  `IconPathProvider`, whereas `PsiFile.getIcon(...)` returns a `DeferredIcon`, `CompositeIcon`, or `RetrievableIcon`
  wrapper. Unwrap `RetrievableIcon.retrieveIcon()` (under a read action), walk `CompositeIcon` children, and inspect
  `DeferredIcon.baseIcon` until you reach the `IconPathProvider`.
- **The icon's classloader can differ from the file type's plugin classloader.** For example, `.gradle` files use the
  Groovy plugin's file type but the Gradle plugin's icon, making `iconKey.iconClass.classLoader` unreliable.
  - Recover the real owner by scanning `PluginManager.getPlugins()` for the plugin whose `classLoader` matches the
    icon's `ImageDataLoader` (`CachedImageIcon.originalLoader` + `ImageDataLoader.isMyClassLoader`), falling back to a
    supplied class only if none matches.

A working bridge helper must perform both steps:

1. **Unwrap dig**: recursively descend `RetrievableIcon`/`CompositeIcon`/`DeferredIcon` (under a read action where
   `retrieveIcon()` requires it) to find an `IconPathProvider`, returning `null` if none exists.
2. **Plugin-classloader search**: match the icon's `ImageDataLoader.isMyClassLoader` against
   `PluginManager.getPlugins()` instead of trusting a single guessed class.

Because `fromPlatformIcon` exists only in the Jewel **bridge**, this helper must no-op or fall back in standalone and
unit-test contexts. Flag naive `IntelliJIconKey.fromPlatformIcon(psiFile.getIcon(0))` calls that omit the unwrap or
classloader search; they throw on non-`IconPathProvider` wrappers or resolve against the wrong classloader in packaged
builds.

## What to flag

- Flag hand-built icon paths or `Image(painterResource("...svg"))` calls for IDE icons instead of using an `IconKey`
  with the `Icon` composable and painter provider, which drops New-UI mapping, dark variants, and theme recoloring.
- A single path for an icon that has distinct old-UI/New-UI variants (use `IntelliJIconKey`).
- Hardcoding a variant filename (`foo_dark.svg`, `foo@2x.png`, `foo@20x20.svg`, `fooSelected.svg`) instead of
  referencing the base name and letting the hint/loader pipeline select — it defeats the automatic
  `_dark`/`@2x`/size/state selection and its graceful fallback. Also flag an invented suffix (e.g., `_dark_stroke`, or
  `@20x20` on a plain icon expecting it to resize) that no loader convention recognizes.
- A **missing base icon** for a stateful control (shipping only state variants, or a typo in the base path). Every
  specialized variant falls back toward the base, so a missing base fails to the magenta error painter; the base is
  required while state/theme/size variants are optional.
- Assuming a Swing stateful control's rendering implies a `fooSelected.svg`/`fooFocused.svg` on the Jewel side — those
  state *filename* suffixes are Jewel-only; the Swing equivalent lives in the LaF, not in base-path suffixes.
- Wrong/missing `iconClass` (icon won't resolve from the right ClassLoader) for plugin-owned icons.
- Flag hardcoded `_dark` paths or PNG assets where theme recoloring requires an SVG loaded through the hint pipeline.
- **Applying layer-level `ColorFilter.tint()` to filled or multi-color icons**, which clobbers fill alpha channels
  and interior shapes to solid colors (use `PainterHint.Stroke` for monochrome stroke icons, or leave filled icons
  untinted).
- **Mixing `PainterHint.Stroke(color)` with layer-level `ColorFilter.tint()`**, which overrides stroke recoloring
  during the draw phase.
- Flag code that captures an IDE icon's painter or bitmap once and holds it across theme switches, which leaves the icon
  stale.
- Bridging a non-resource/synthetic Swing icon via `fromPlatformIcon` (only `IconPathProvider`/resource-backed icons
  work).
- **Cross-plugin/cross-module icon references**: flag icon lookups that resolve against the wrong classloader and break
  in packaged builds when plugin classloaders are isolated.
- **Naming another plugin's icon directly for a foreign file or PSI** (using its icon constant or a reflective
  `...Icons.` path). Ask the platform instead via `PsiElement.getIcon(flags)` / `PsiIconUtil.getIconFromProviders` for
  PSI, or `IconUtil.getIcon`/`computeFileIcon(file, flags, project)` for a `VirtualFile`, so the owning plugin's
  provider supplies the icon on its own classloader.
- A feature-rich SVG used on a Jewel surface with no verification that Skia renders it correctly (the Swing JSVG render
  is not evidence) — and vice versa for Swing surfaces.
- A `ResourcePainterProvider` constructed outside `remember` (per-recomposition), discarding its cache and re-decoding
  the SVG each time — use `rememberResourcePainterProvider`.
- Assuming the platform's global/disk icon cache speeds up the Jewel render path (it does not; Jewel caches only
  in-memory, per provider).
- Use of the experimental `com.intellij.platform.icons` framework in shipping plugin UI — see
  `icon-loading-experimental.md`.

## Cross-references

- Read `ijpl-theming-and-editor-scheme.md` for theme-change staleness and the bridge color analogue.
- Standalone-vs-bridge model: `standalone-vs-bridge.md`.
- Jewel component/icon catalog usage (which key, `AllIconsKeys`, `IconKey` APIs when authoring): defer to the `jewel-ui`
  skill; this reference is about loading correctness, not catalog choice.
