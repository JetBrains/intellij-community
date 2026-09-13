# Icon Loading (Jewel + existing IJPL machinery)

Use when reviewing how Compose/Jewel UI in an IntelliJ-based IDE loads and renders icons with the **current, bundled**
systems. Icon loading is a deep rabbit hole; this reference maps the contract, the classloader traps, and the failure
modes. It is grounded in the Jewel and IJPL sources; confirm symbol names against the local sources before proposing
code.

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

- **Swing/IJPL path**: `IconLoader`/`AllIcons` → `CachedImageIcon` → platform rendering. This is what Swing UI and any
  `javax.swing.Icon` use. It applies the platform's dark-variant selection, New-UI path mapping, SVG color patching,
  scaling/DPI, deferred icons, and icon patchers/overlays.
- **Jewel path**: `IconKey` → `ResourcePainterProvider` → `Painter` → `Icon` composable. Jewel **reimplements** icon
  loading: its own set of `ClassLoader`s, its own resource read, its own SVG parsing (`DocumentBuilderFactory` + Compose
  `decodeToSvgPainter`/`decodeToImageVector`), its own painter cache, and its own `PainterHint` pipeline. On failure, it
  does **not** throw or fall back to a platform icon — it logs and substitutes a **magenta `ColorPainter`** (a useful
  visual tell that a Jewel icon failed to load).
- **The bridge feeds data, not rendering.** In the IDE, Jewel does not render through `IconLoader`.
  `BridgePainterHintsProvider` (a `PalettePainterHintsProvider`) reads platform data — New-UI icon path mappings and the
  theme color palette (`UITheme.getColorPalette()`) — and exposes it as `PainterHint`s that **Jewel's own**
  `ResourcePainterProvider` consumes. `IntelliJIconKey.fromPlatformIcon(swingIcon)` only extracts the path strings (
  `originalPath`/`expUIPath`) from a platform icon; it never renders it via `IconLoader`.

Review consequences of the split:

- **Platform-only icon features do not automatically apply to Jewel icons.** Deferred/dynamic icons, `IconLoader`
  patchers, and other Swing-side behaviors are part of the Swing path; a Jewel `Icon` rendered from an `IconKey` goes
  through Jewel's reimplementation and only gets what the `PainterHint` pipeline provides (path mapping and palette
  recolor). Do not assume an effect seen on a Swing icon carries over to the Jewel render of the same key.
- **A magenta icon is a Jewel-load failure**, not a generic "icon missing" — it specifically means
  `ResourcePainterProvider` could not load/parse the resource (wrong path, resource not on its classloaders, malformed
  SVG). Treat magenta as a Jewel-path diagnostic, and contrast it with the Swing path when useful: classic Swing icon
  misses often render blank/silent rather than magenta.
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
- `IconLoader` applies dark-variant selection, New-UI path mapping, SVG color patching against the theme, scaling/DPI,
  deferred icons, and patchers/overlays. Jewel's `PainterHint` pipeline is a **separate reimplementation** of the
  path-mapping + recolor subset, not a call into `IconLoader`; the two are parallel, and only `IconLoader`'s subset is
  mirrored on the Jewel side. Do not hand-roll either pipeline; use the appropriate public APIs.
- Icons are lazy/cached and can be *deferred*; an icon instance may resolve differently after a theme change. Capturing
  a one-time `Painter`/bitmap from an IDE icon and holding it across theme switches is the icon analogue of the frozen-
  `JBColor` bug in `ijpl-theming-and-editor-scheme.md`. On the Jewel path the analogous concern is a `PainterProvider`/
  `Painter` not re-resolved across a theme change.

## Icon filename variants (`@2x`, `_dark`, `_stroke`, `@WxH`)

You ship **one** base path (e.g., `/icons/foo.svg`) and the loader resolves sibling files by appending suffixes; you do
not reference the variants directly. The variant filenames are built by a literal `base + suffix + "." + ext`
transform, and the loader tries a candidate list in priority order (Swing path: `ImageDescriptor`/
`createImageDescriptorList` in `com.intellij.ui.icons`). The suffixes, from source:

- **`_dark`** — dark-theme variant (`foo_dark.svg`). Applies to both SVG and raster.
- **`@2x`** — HiDPI/retina variant, triggered when the pixel scale ≠ 1. For **raster** (`foo@2x.png`) it is a separate
  file containing a bitmap at twice the pixel dimensions (a 32×32 PNG for a 16×16 icon); the loader tags that variant
  with a density of `2f` so it's drawn into the same logical 16×16 space at full crispness on a HiDPI screen. For
  **SVG on the Swing path**, `@2x` is *also* looked for (intentionally — the source comments say so), acting mainly as a
  `data-scaled` normalization hint since SVG is vector; so `foo@2x.svg` is a real filename to the Swing loader. **The
  Jewel path differs — it does not use `@2x` for SVG at all** (see the Jewel note below); don't assume `foo@2x.svg` is
  picked up there.
- **Combined dark + HiDPI** — there are three dark candidate filenames — `foo@2x_dark`, `foo_dark@2x`, and `foo_dark` —
  and the loader just reorders them to put the one matching the current screen first. On a **HiDPI screen** it tries
  the `@2x` ones first: `foo@2x_dark` → `foo_dark@2x` → `foo_dark`. On a **non-HiDPI screen** it tries the plain one
  first, i.e., the exact reverse: `foo_dark` → `foo_dark@2x` → `foo@2x_dark`. Either way all three dark names are
  attempted (so a mismatched-but-present variant is still found) before falling through to the non-dark names. Ship dark
  variants as `foo_dark.svg` (and additionally `foo@2x_dark.png` for raster dark HiDPI).
- **`_stroke`** — the monochrome "stroke" variant used in selected/highlighted contexts (`foo_stroke.svg`), tried first
  when a stroke icon is requested. Note: there is **no** `_dark_stroke`/`_stroke_dark` convention in the loader — don't
  invent one.
- **`@WxH` (e.g., `@20x20`)** — a real convention but a **narrow** one: it selects a *size-specialized* variant file
  (used for New UI toolwindow icons, where `toolWindowFoo@20x20.svg` is the 20×20 New UI variant and
  `toolWindowFoo.svg` the 16×16 compact one). It is **not** how an ordinary SVG's intrinsic size is determined — that
  comes from the SVG's own `width`/`height` attributes (defaulting to 16×16 when absent), never from an `@WxH` suffix
  on a plain icon. Don't add `@20x20` to a normal icon expecting it to resize it; size your SVG via its `width`/
  `height`.

Review implications:

- **Reference the base name only**, and ship the variant files beside it with the exact suffix spelling above. A
  component that hardcodes `foo_dark.svg` (or `foo@2x.svg`) directly defeats the loader's automatic selection — flag it
  (same finding class as bypassing the `PainterHint` pipeline).
- **The suffix order is `@2x` then `_dark` in the combined form** (`foo@2x_dark`), and dark HiDPI is preferred to the
  reverse; a variant named `foo_dark@2x` will still be found (it's second in the list) but the canonical spelling per
  the SDK docs is `iconName@2x_dark.svg`.
- On the **Jewel** side this suffix logic lives in the `PainterHint` pipeline (the `PainterSuffixHint` subclasses:
  `Dark` → `_dark`, `HiDpi` → `@2x`, `Stroke` → `_stroke`, `Size` → `@WxH`), *not* in `IconLoader` — and it does **not**
  match the Swing loader exactly. The key divergence: **Jewel does not apply `@2x` to SVGs.** Its `HiDpi()` hint only
  applies to bitmaps (`canApply()` is gated and its own KDoc says `my-icon.svg → N/A`, noting "the IntelliJ Platform
  tends to use only `Size` for SVGs and only `HiDpi` for PNGs"). So on the Jewel path an SVG is scaled via the **`Size`
  hint** (`my-icon@20x20.svg`) or just its intrinsic `width`/`height`, and a bare `my-icon@2x.svg` is **not** looked
  for — whereas the Swing loader *does* look for `foo@2x.svg` by intention. Concretely: ship `_dark` variants for both
  paths; ship `@2x` only for **PNG**; for SVG rely on intrinsic size or an explicit `@WxH` size variant. Do not count on
  a `foo@2x.svg` being picked up on a Jewel surface. (`Dark`/`HiDpi`/`Stroke`/`Size` in
  `org.jetbrains.jewel.ui.painter.hints`; wired in `StandalonePainterHintsProvider.hints()` as `HiDpi()` + `Dark(...)`.)

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

**The important part is the fallback mechanism on missing icon variants.** Jewel's `ResourcePainterProvider` applies
each path hint as a *branch*, not a mutation: for every `PainterPathHint` it expands the candidate list into "path with
the suffix applied" **and** "path without it" (`scopes = scopes.flatMap { listOfNotNull(it.apply(hint), it) }`), then
loads the **first candidate that actually exists on the classpath** (`firstNotNullOfOrNull { resolveResource(it) }`).
So the effective order runs from the most-specific composite name down to the bare base name, and **any missing
specialized variant simply falls through to the next-less-specific file** — ending at the base icon. Practically:

- You do **not** need to ship every state×theme×size combination. Ship the base (`checkBox.svg`), add only the variants
  that actually differ (`checkBoxSelected.svg`, `checkBoxDisabled.svg`, `_dark` versions), and the rest degrade to base
  automatically. A missing `Hovered` variant just renders the at-rest icon on hover — not an error.
- The one hard failure is when **no** candidate in the branch — including the base — resolves: the provider logs
  `Resource '…' not found` and returns the **magenta error painter** (the same visible tell noted earlier). So the base
  must exist; the specialized ones are optional. Say this explicitly in reviews: missing specialized variants degrade to
  base; missing base turns into Jewel's magenta diagnostic, whereas the analogous Swing path often fails blank/silent.
- Because state/selection are driven by live component state, the resolved painter changes as the user interacts; a
  provider/`Painter` captured once and reused (outside the keyed `getPainter`/`remember`) will show a stale state
  variant — same captured-`Painter` hazard called out above.

**Swing side:** these `Selected`/`Focused`/`Pressed`/`Hovered`/`Disabled` *filename* suffixes are a **Jewel** concept
— the classic `ImageDescriptor`/`IconLoader` variant list only covers `_dark`/`@2x`/`_stroke`/`@WxH`, not interaction
state. In Swing, stateful control icons come from the Look-and-Feel (state-specific `UIManager`/LaF icon keys and the
component UI), not from state suffixes on one base path. So when porting a stateful Swing control to Jewel, expect the
state axis to move into these hint suffixes; do not assume a `fooSelected.svg` exists just because the Swing control had
a selected rendering. (Jewel: `Selected`/`Stateful` in `org.jetbrains.jewel.ui.painter.hints`; branch-and-fall-back in
`ResourcePainterProvider.loadPainter`.)

## Two different SVG renderers (Swing vs Skia)

The two render paths also use **different SVG engines**, which is its own rabbit hole. This is a genuine source of "the
same `.svg` looks right in Swing but wrong/blank in the Jewel render" (or vice versa).

- **Swing/IJPL** rasterizes SVG with **JSVG** (JetBrains' use of `com.github.weisj.jsvg`, a pure-Java SVG renderer; see
  `com.intellij.ui.svg`).
- **Jewel** (Compose Multiplatform → Skiko) decodes SVG via CMP's `decodeToSvgPainter`, which is backed by **Skia's**
  SVG support (accessed over JNI in `org.jetbrains.skia.svg`).

These are independent implementations with **different SVG feature coverage and rendering quirks**. The practical
consequences for review:

- An icon authored/verified against one engine is **not guaranteed** to render identically (or at all) on the other.
  Effects, filters, gradients, masks, text, and less-common SVG features are the usual divergence points — but exactly
  which features differ is a moving target across versions of JSVG and Skia.
- **Do not trust a hardcoded capability matrix here (including from this skill).** The honest guidance is: when an icon
  must render in both a Swing surface and a Jewel surface, **verify the actual asset in both**, rather than assuming
  parity. Treat "renders correctly in the IDE's Swing UI" as no evidence it renders correctly through Jewel/Skia, and
  vice versa.
- Keep IDE icon SVGs to the simple, conventional subset the platform's own icons use (basic shapes/paths, palette-driven
  fills). Unusual SVG features are the most likely to diverge between engines. Flag richly-featured SVGs used in Jewel
  surfaces as needing explicit Skia-render verification.
- Jewel can also load Android-style XML vector drawables via `decodeToImageVector` (the `.xml` path) — a format the
  Swing path does not use. An asset relying on one format does not transparently work on the other path.

This is a known-divergence flag, not a precise spec: the value is reminding the reviewer that two engines exist and the
asset must be tested on the one(s) it will actually render on.

## Caching and icon lifecycle (the two cache models differ sharply)

The Swing/IJPL and Jewel paths cache very differently. Getting this wrong shows up as stale icons, surprising memory
cost, or repeated decode work. Grounded in the current sources:

**Swing/IJPL — global + persistent, multi-tier:**

- A **global icon cache**: Caffeine, keyed `Pair<String, ClassLoader?>` → `CachedImageIcon`, `maximumSize(256)`,
  `expireAfterAccess(30 minutes)` (`IconLoader`). Shared process-wide. Note its invalidation model: on a theme/transform
  change the `CachedImageIcon` instances are **not** evicted — they are long-lived and re-resolve their rasterization on
  the fly (a global transform mod-count is bumped and registered cleaners run, clearing the downstream image/IO caches
  below). The Caffeine `iconCache` itself is only fully invalidated in tests (`clearCacheInTests`), or selectively via
  `detachClassLoader`. Do not describe `clearCache()` as flushing the icon cache; it bumps the transform count and runs
  cleaners.
- An **in-memory rasterized-image cache**: Caffeine, keyed by `CacheKey(path, scale, digest)` → `BufferedImage`,
  `expireAfterAccess(30 seconds)` (`com.intellij.ui.icons.imageCache`).
- A **persistent on-disk cache** of rasterized SVGs: `SvgCacheManager`, backed by an **H2 MVStore** at
  `<system>/icon-cache-v2.db`. Rasterized SVG output **survives across IDE restarts**, keyed by content/scale. There is
  also an `ioMissCache` to avoid retrying known-missing resources.
- Net: an `AllIcons`/`IconLoader` icon is cheap to ask for repeatedly — it is shared globally and its rasterization is
  memoized in memory and on disk.

**Jewel — in-memory, provider-local only (no disk, no global):** The cache chain spans repos (`intellij-community`
Jewel → `compose-multiplatform` resources → `skiko` → Skia). Note the current SVG path is the CMP **resources** library
(`org.jetbrains.compose.resources`, `compose-multiplatform` repo); the older Compose Desktop SVG loaders in
`compose-multiplatform-core` (`androidx.compose.ui.res.SVGPainter`/`loadSvgPainter`) are **deprecated** in favor of
it,
so review/verify against the resources package, not the core one. What is cached at each level, verified in source:

- **Parse (SVGDOM): cached once.** Jewel's `ResourcePainterProvider` calls CMP `decodeToSvgPainter` on a cache miss and
  stores the resulting `Painter` in its own `ConcurrentHashMap<Int, Painter>` (key = accepted-hints hash * 31 + density
  hash). CMP's `decodeToSvgPainter` parses the bytes into a Skiko `SVGDOM` once and wraps it in an `SvgPainter`; the
  `SVGDOM` is reused for the life of that `Painter`. So the SVG is **parsed once**, not per draw.
- **Rasterize-to-bitmap: intended, but effectively per-draw in current source.** `SvgPainter` holds a `DrawCache` meant
  to rasterize the `SVGDOM` into an `ImageBitmap` once and blit it (its own comment claims "3x-4x higher FPS"). However,
  in the current source the guard `if (previousDrawSize != size)` in `onDraw` references a `previousDrawSize` field that
  is **never assigned**, so the guard is always true and `drawCache.drawCachedImage { drawSvg(size) }` re-runs
  `SVGDOM.render` into the bitmap on every draw. Net effect today: the bitmap cache exists but its frame-to-frame
  memoization is defeated, so a repeatedly-drawn Jewel SVG re-rasterizes per draw. (Verified in the current CMP
  **resources** source — `compose-multiplatform`
  `components/resources/library/.../org/jetbrains/compose/resources/SvgPainter.kt`
  and `DrawCache.kt` — and **confirmed by the Compose Multiplatform team** as a
  real bug: [CMP-10433](https://youtrack.jetbrains.com/issue/CMP-10433). This is a code-level detail that can change
  between versions — re-check (and watch that issue) before relying on it; the point for review is the *shape*, not a
  guarantee.)
- **Skiko/Skia: no SVGDOM raster memoization.** `SVGDOM.render(canvas)` delegates straight to native `SkSVGDOM::render`
  and re-traverses/re-rasterizes the vector tree each call; Skia's resource/GPU caches do not memoize a whole SVGDOM's
  output. Cheap repeat draws require an explicit offscreen `Surface` + `makeImageSnapshot()` — which is exactly what
  `DrawCache` is *trying* to be.
- **No global cache, no disk cache, no cross-provider sharing.** The provider's map is the only Jewel-side cache.
  Providers come from `rememberResourcePainterProvider(...)`, `remember`-keyed on `iconKey`/`classLoader`/`isNewUi`, so
  **cache lifetime = composition lifetime.** Confirmed: Jewel never touches `SvgCacheManager` or `IconLoader` (no
  references in `platform/jewel`); the bridge only borrows `patchIconPath` for path mapping, not loading/caching.

Review consequences:

- **Don't recreate providers on the hot path.** Constructing a `ResourcePainterProvider` ad hoc (instead of
  `rememberResourcePainterProvider`) inside a lazy-list item or frequently-recomposed scope discards the per-instance
  parse cache every recomposition, forcing a re-parse (new `SVGDOM`) each time. Flag provider construction not behind
  `remember`.
- **Jewel does not benefit from the platform's disk/global icon cache.** The same asset served from `SvgCacheManager`/
  `IconLoader` on a Swing surface is, on a Jewel surface, parsed-once-in-the-provider and (today) re-rasterized per draw
  with no persistent/global memoization (the per-draw re-rasterization being the confirmed CMP-10433 bug above). Do not
  assume platform icon caching speeds up the Jewel render path.
- **Invalidation is asymmetric.** On the Swing side, `CachedImageIcon`s adapt to theme/transform changes in place (the
  icon cache is not flushed; a transform mod-count drives re-resolution, and the image/IO caches are cleared by
  cleaners); the disk cache is content-keyed. On the Jewel side, provider caches just drop when the composition leaves,
  and theme changes are handled by re-running the keyed `remember`/re-resolving hints. A provider/`Painter` captured
  outside the keyed `remember` can serve stale results across a theme switch (same family as the captured-`Painter`
  staleness above).
- **Memory shape differs.** Swing caps the icon cache (256, plus a 32MB weighted image cache) and expires entries;
  Jewel's per-provider maps are unbounded but die with the composition. Long-lived composables resolving many distinct
  icons through one provider hold all those painters for the provider's lifetime.

## Classloader and classpath traps (the "works from sources, broken in release" class)

This is the highest-value, easiest-to-miss part. Icons are bound to a **`ClassLoader`**, and the binding behaves
differently in a dev/sources run than in a release build.

- **The icon cache key is `Pair<String, ClassLoader?>`.** An icon resolved for one classloader is a different cache
  entry from the same path under another. `IconLoader.getIcon`/`findIcon` overloads take either a `Class<*>` (uses
  `aClass.classLoader`) or an explicit `ClassLoader`; the deprecated no-arg path infers the caller class reflectively.
  The classloader is not incidental — it is part of the icon's identity and resolution.
- **Resource resolution uses that classloader.** A `CachedImageIcon` is backed by a loader that resolves the SVG/PNG
  resource from its bound classloader (`isMyClassLoader`, `detachClassLoader`). If the resource is not on that
  classloader's classpath, the icon does not load.
- **Reflective icon paths cross-load a class.** A path like `AllIcons.General.Add` (a "reflective" path: contains
  `Icons.`, not `/`-prefixed, not `.svg`) is resolved by `getReflectiveIcon`, which does
  `classLoader.loadClass(className)` then reads the static `Icon` field. So referencing **another plugin's** icon-holder
  class (or even a platform one) requires that class to be loadable from the classloader you passed.
- **Why it works from sources but not in release.** In a dev/sources run the module classpath is effectively flat, so a
  class from another plugin/module is often loadable from whatever classloader is in play, and the icon resolves. In a
  packaged build, each plugin has its **own isolated classloader**; loading another plugin's icon class (or a resource
  that lives in another plugin) from your classloader fails, and the icon silently goes missing. This is a real,
  recurring trap that does not show up until the release/packaged build.

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

A very common trigger for the release-only trap is needing the icon for a file whose type is owned by **another
plugin** — e.g., showing a `KtFile` (Kotlin plugin), a Gradle/Groovy file, a framework config — from a plugin that does
**not** (and often *cannot*) depend on that plugin. You must not reach for the owning plugin's icon constants
(`KotlinIcons.FILE`, its `AllIcons`-style holder, or a reflective `...Icons.` path): those classes live on the other
plugin's isolated classloader and will fail to load in a packaged build even though they resolve from a flat sources
run.

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

**On a Jewel surface it is genuinely fiddly**, and worth understanding in depth. To turn that platform Swing icon into a
Jewel `IconKey` (via `IntelliJIconKey.fromPlatformIcon`) two things bite:

- **The icon usually isn't directly an `IconPathProvider`.** `IntelliJIconKey.fromPlatformIcon` hard-requires an
  `IconPathProvider` (it `check`s the type), but `PsiFile.getIcon(...)` typically hands back a wrapped/`DeferredIcon`/
  `CompositeIcon`/`RetrievableIcon` composite, not a bare path-backed icon. You have to *dig* for the path-backed
  sub-icon: unwrap `RetrievableIcon.retrieveIcon()` (under a read action), walk `CompositeIcon` children, and check
  `DeferredIcon.baseIcon`, until you find the `IconPathProvider`. Several layers deep is normal.
- **The icon's classloader is not necessarily the file type's plugin classloader.** They can diverge: e.g., `.gradle`
  files use the **Groovy** plugin's file type but the **Gradle** plugin's icon. So `iconKey.iconClass.classLoader` or
  any single guessed class is not reliably correct. The robust move is to recover the real owner by scanning
  `PluginManager.getPlugins()` for the plugin whose `classLoader` the icon's own `ImageDataLoader` accepts
  (`CachedImageIcon.originalLoader` + `ImageDataLoader.isMyClassLoader`), falling back to a supplied class only if none
  matches.

So a correct bridge helper for this case has to do two things by hand: (1) an **unwrap dig** — recursively descend
`RetrievableIcon`/`CompositeIcon`/`DeferredIcon` (under a read action where `retrieveIcon()` needs it) until it finds an
`IconPathProvider`, returning null if none; and (2) a **plugin-classloader search** — resolve the owning classloader
via the icon's `ImageDataLoader.isMyClassLoader` against `PluginManager.getPlugins()` rather than trusting a single
guessed class. Both are guarding real platform quirks; also note such a helper only works under the Jewel **bridge**
(`fromPlatformIcon` doesn't exist in standalone), so it must no-op or fall back in unit-test/standalone contexts. If you
see a plugin doing the naive `IntelliJIconKey.fromPlatformIcon(psiFile.getIcon(0))` with no unwrap and no classloader
search, that's a bug on both counts — it will throw on the non-`IconPathProvider` icon and/or resolve against the wrong
classloader in a packaged build.

## What to flag

- Hand-built icon paths or `Image(painterResource("...svg"))` for IDE icons instead of an `IconKey` + the `Icon`
  composable/painter provider (loses New-UI mapping, dark variants, theme recoloring).
- A single path for an icon that has distinct old-UI/New-UI variants (use `IntelliJIconKey`).
- Hardcoding a variant filename (`foo_dark.svg`, `foo@2x.png`, `foo@20x20.svg`, `fooSelected.svg`) instead of
  referencing the base name and letting the hint/loader pipeline select — it defeats the automatic
  `_dark`/`@2x`/size/state selection and its graceful fallback. Also flag an invented suffix (e.g., `_dark_stroke`, or
  `@20x20` on a plain icon expecting it to resize) that no loader convention recognizes.
- A **missing base icon** for a stateful control (only state variants shipped, or a typo'd base path): every specialized
  variant falls back toward the base, so if the base itself is absent the whole branch fails to the magenta error
  painter. The base must exist; state/theme/size variants are optional.
- Assuming a Swing stateful control's rendering implies a `fooSelected.svg`/`fooFocused.svg` on the Jewel side — those
  state *filename* suffixes are Jewel-only; the Swing equivalent lives in the LaF, not in base-path suffixes.
- Wrong/missing `iconClass` (icon won't resolve from the right ClassLoader) for plugin-owned icons.
- A hardcoded `_dark` path or a PNG where theme recoloring is needed (should be SVG through the hint pipeline).
- Capturing an IDE icon's painter/bitmap once and holding it across theme changes (stale after theme switch).
- Bridging a non-resource/synthetic Swing icon via `fromPlatformIcon` (only `IconPathProvider`/resource-backed icons
  work).
- **Cross-plugin/cross-module icon references resolved against the wrong classloader** — the release-only breakage class
  above. The single most under-tested icon bug.
- **An icon for a file/PSI owned by another plugin obtained by naming that plugin's icon** (its icon constant or a
  reflective `...Icons.` path) instead of asking the platform — use `PsiElement.getIcon(flags)` /
  `PsiIconUtil.getIconFromProviders` for PSI, or `IconUtil.getIcon`/`computeFileIcon(file, flags, project)` for a
  `VirtualFile`, so the owning plugin's `IconProvider`/`FileIconProvider` supplies it on its own classloader.
- A feature-rich SVG used on a Jewel surface with no verification that Skia renders it correctly (the Swing JSVG render
  is not evidence) — and vice versa for Swing surfaces.
- A `ResourcePainterProvider` constructed outside `remember` (per-recomposition), discarding its cache and re-decoding
  the SVG each time — use `rememberResourcePainterProvider`.
- Assuming the platform's global/disk icon cache speeds up the Jewel render path (it does not; Jewel caches only
  in-memory, per provider).
- Use of the experimental `com.intellij.platform.icons` framework in shipping plugin UI — see
  `icon-loading-experimental.md`.

## Cross-references

- Theme-change staleness and the bridge color analogue: `ijpl-theming-and-editor-scheme.md`.
- Standalone-vs-bridge model: `standalone-vs-bridge.md`.
- Jewel component/icon catalog usage (which key, `AllIconsKeys`, `IconKey` APIs when authoring): defer to the `jewel-ui`
  skill; this reference is about loading correctness, not catalog choice.
