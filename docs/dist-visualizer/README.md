# IDEA Dist Plugin-Model Visualizer

Single-file HTML visualizer for an installed JetBrains IDE distribution, focused on
the **Plugin Model v2** and **Product DSL** concepts: plugins, content modules,
loading modes (`required` / `embedded` / `optional` / `on-demand`), embedded modules,
visibility, dependency edges, and boot classpath.

## Scope and extraction

Every module in the viewer is classified along two orthogonal axes, and the
row iconography in every list reflects both at a glance:

- **Scope** — where the module's classes live.
  - `core` — packed in a jar under `lib/` (loaded directly by the platform's
    `RuntimeModuleRepository`). Teal-filled tile in row icons.
  - `plugin` — packed in a jar under `plugins/X/lib/` (owned by exactly one
    plugin). Blue-outlined tile.
- **Extraction** — is the module the sole tenant of its jar?
  - `extracted` — owns a dedicated jar (e.g. `lib/intellij.platform.core.jar`).
    Solid tile in the icon.
  - `embedded` — shares its jar with other modules (e.g. `lib/util-8.jar`
    packs 17 legacy core modules, `lib/intellij.platform.ide.impl.jar` packs
    23 modules, plugin main jars routinely pack many content modules). Tile
    with partition lines in the icon.

The signal for both axes is the per-module `<resource-root path="…"/>` inside
`modules/module-descriptors.jar`. Modules that have a descriptor but no
`productInfo.layout[]` entry (typically `$legacy_jps_module` namespace, e.g.
`intellij.platform.util`) are surfaced as `core` modules so that ongoing
extraction progress is fully visible.

The Overview tab has dedicated *Core modules → extraction state* cards plus a
*Container jars* table that lists shared `lib/*.jar` monoliths and their
tenant counts. The Modules tab has explicit `core | plugin-content` and
`extracted | embedded` filter chips and a "Container jar" dropdown.

## Run

```bash
# default: /Applications/Idea.app
bun community/docs/dist-visualizer/visualize.mjs

# or any installed JetBrains IDE
bun community/docs/dist-visualizer/visualize.mjs /Applications/PyCharm.app

# Linux/Windows: pass the install root (the dir containing product-info.json + lib/)
bun community/docs/dist-visualizer/visualize.mjs /opt/idea-IC-262.SNAPSHOT
```

The script reads `product-info.json` and `modules/module-descriptors.jar` from the
distribution, builds an enriched data model, writes a self-contained HTML file to
the OS temp dir (`idea-dist-viewer-<productCode>-<build>.html`), and opens it in
the default browser.

No build step. No `pnpm install`. No HTTP server. ECharts loads from a CDN
(`cdn.jsdelivr.net`); the rest of the page is inlined.

## What the views show

- **Overview** — product, layout counts per kind (`plugin`, `moduleV2`,
  `productModuleV2`, `pluginAlias`), loading-mode distribution, top 10 plugins by
  aggregate size.
- **Plugins** — searchable list. Expanding a plugin shows its descriptor module,
  main classpath, content modules with their loading mode, and all modules whose
  classpath lives in the plugin's directory.
- **Modules** — searchable list of `moduleV2` + `productModuleV2` (plus
  descriptor-only legacy core modules). Each row shows the scope/extraction
  icon, loading mode, host jar (click to filter by that container), visibility,
  owner plugin, and size. Filter chips for `core` / `plugin-content`,
  `extracted` / `embedded`, loading mode, and visibility.
- **Treemap** — ECharts treemap of dist size. Three modes: `container jar →
  tenant` (groups by scope then by jar, highlighting shared monoliths),
  `kind → plugin → jar`, or `plugin → module → jar`.
- **Graph** — ECharts force-directed graph of module dependencies. Pick a plugin
  to scope the view; depth slider controls neighborhood hops. Node color = kind,
  border style = loading mode (solid = `required`/`embedded`, dashed = `optional`,
  dotted = `on-demand`); embedded modules have a brighter border.
- **Boot** — ordered `bootClassPathJarNames` for the selected OS/arch, with sizes.

## Offline use

Replace the ECharts CDN tag in `lib/template.mjs` with a vendored copy:

```bash
curl -sLo community/docs/dist-visualizer/assets/echarts.min.js \
  https://cdn.jsdelivr.net/npm/echarts@5.5.1/dist/echarts.min.js
```

Then change `ECHARTS_CDN` in `lib/template.mjs` to inline its contents into the
generated HTML.

## Implementation

```
visualize.mjs           # CLI entry — read dist, build HTML, open browser
lib/zip.mjs             # tiny STORE+DEFLATE zip reader (zlib.inflateRawSync)
lib/descriptors.mjs     # parse module-descriptors.jar XMLs
lib/product-info.mjs    # parse product-info.json, attach jar sizes, merge w/ descriptors
lib/template.mjs        # buildHtml(data) — inlines CSS+JS+data into one HTML file
assets/viewer.css       # dark IntelliJ-ish theme
assets/viewer.js        # tabbed SPA — vanilla JS, no framework
```

Reference docs:

- `docs/IntelliJ-Platform/4_man/Plugin-Model/Plugin-Model-v1-v2.md` (the spec for
  loading modes, classloaders, visibility, embedded modules).
- `community/platform/build-scripts/product-dsl/docs/` (how products compose module
  sets — the source of what shows up in `product-info.json` `layout[]`).
