# Theme Key Resolution and `.theme.json` Gotchas

How an IntelliJ `*.theme.json` becomes the `UIDefaults` (the Swing LaF map) that the bridge then reads from. Use this
when reasoning about *why* a key resolves (or fails to resolve) to the value you expect, when a theme override "doesn't
take," or when reviewing a hand-written or generated theme file. It is the load-time companion to
`theming-and-color.md` (which covers role semantics) and `standalone-vs-bridge.md` (which covers reading the result back
from Compose).

Everything here is from the platform loader: `UITheme.kt`, `UIThemeBean.kt`, `uiThemeParser.kt`, `ColorMap.kt` (in
`platform/platform-impl/src/com/intellij/ide/ui/`), and the runtime `*` fallback in `JBColor.java` (
`platform/util/ui/src/com/intellij/ui/`). Cite those when a finding is questioned. The loader evolves; treat specific
key names as illustrative and re-verify against the build you review.

## The top-level sections of a `.theme.json`

A theme file is parsed (`readTheme` in `UIThemeBean.kt`) into a fixed set of sections. They are **not**
interchangeable — each is resolved by a different code path, and putting a value in the wrong section usually doesn't do
what you intended (e.g., a LaF key placed under `colors` becomes a `ColorPalette.<name>` entry rather than affecting
that LaF key; unknown top-level *objects* are warned and dropped, while unknown scalar fields may be silently ignored):

- **`colors`** — the *named color palette* (e.g., `"gray-10": "#191A1C"`, `"blue-50": "#2A4371"`). A private dictionary
  of name→color (and name→name aliases). These are **not** LaF keys on their own; they exist to be referenced by other
  sections. At apply time each becomes a `ColorPalette.<name>` UIDefaults entry (see "What lands in UIDefaults").
- **`ui`** — the *component LaF keys* (e.g., `"Button.startBackground"`, `"Panel.background"`, `"*.borderColor"`). This
  is the bulk of a theme. Values are either inline (`"#RRGGBB"`, numbers, insets) or a **reference into `colors`** by
  name. Nested objects are flattened (see "Flattening").
- **`icons`** — icon path overrides and, as a nested child, the `ColorPalette` SVG-recolor map (distinct from the
  `colors` section; this one recolors icon SVGs, e.g., `"#6C707E": "Gray.Stroke"`).
- **`iconColorsOnSelection`** — alternate icon colors used when an item is selected.
- **`background`/`emptyFrameBackground`** — IDE frame background image/fill config.
- **Top-level scalars** — `name`, `parentTheme`, `dark`, `editorScheme`, `author`, `nameKey`, `resourceBundle`. Note
  `editorScheme` points at a *separate* `.xml` editor color scheme — the UI theme and the editor scheme are two
  different systems (see `ijpl-theming-and-editor-scheme.md`).

Gotcha: an `id` field in the JSON is **ignored with a warning** — the id comes from the `themeProvider` registration in
XML, not the file. A `UIDesigner` block is skipped entirely. Unknown top-level objects are warned and dropped.

## Resolution order (load time)

When a theme is loaded (`createTheme`), the steps happen in this order, and the order matters:

1. **Parent import** (`importFromParentTheme`) — if the theme has a `parentTheme` (or, when absent, the default
   Light/Dark experimental parent), the parent's `ui`, `icons`, `background`, `colorMap`, and `iconColorOnSelectionMap`
   are merged in **parent-first, child-overrides**: the child starts from a copy of every parent entry, then its own
   entries overwrite by key. Keys the child never mentions are inherited verbatim. This is transitive up the whole
   chain. The nested `icons.ColorPalette` sub-map is union-merged too, not replaced wholesale.
2. **Named-color resolution** (`initializeNamedColors`) — the `colors` map is resolved: direct `#hex` entries become
   colors; **name→name aliases are dereferenced** (one level, then a second pass for alias-of-alias). An alias to an
   undefined name resolves to **`Gray.TRANSPARENT`** with a warning — *not* a hard failure, so a typo'd color name
   produces an invisible/transparent value rather than an error.
3. **Apply to UIDefaults** (`applyTheme`) — see next section.

Because parent import happens *before* named-color resolution, a child can reference a color name defined only in its
parent, and vice versa — the maps are already merged by the time names are resolved.

## What lands in UIDefaults (`applyTheme`)

This is the crux, and the source of the most surprising behavior:

1. Every `colors` entry is written as **`ColorPalette.<name>`** (e.g., `gray-10` → `ColorPalette.gray-10`; classic
   `Gray1` → `ColorPalette.Gray1`). This is why the Jewel bridge reads palette colors under the `ColorPalette.` prefix,
   and why the Islands vs. classic key-format difference matters (see `theming-and-color.md`).
2. Every `ui` entry is written under its own (flattened) key. If the value is a **string that matches a name
   in `colors`, it is replaced by that color**; otherwise it is parsed by type (see "Value parsing"). So
   `"Panel.background": "gray-10"` resolves to the palette color, while `"Panel.background": "#191A1C"` is an inline
   literal — both end up the same color, but only the former tracks the palette.

### The `*` wildcard key — suffix patterning

A `ui` key that starts with `*.` (e.g., `"*.borderColor"`, `"*.background"`) is a **suffix pattern**, applied two ways
at load time (`applyTheme` + `addPattern`):

- **Eager pass:** when the `*.` key is processed, every `UIDefaults` key **already present at that moment** whose name
  **ends with** the tail (`.borderColor`) is overwritten with the pattern's value. So `*.borderColor` retroactively
  overrides the `Button.borderColor`, `ComboBox.borderColor`, etc. that already exist — including specific keys
  inherited from the parent/platform defaults or set earlier in the same `ui` map. "Already present" is literal: a
  specific key written *later* in the same `ui` map is applied after the pattern, so it overwrites the pattern back (see
  the order gotcha).
- **Lazy map:** the pattern is also stored in a special `"*"` entry (a `Map`) for keys that *don't exist yet* at apply
  time. At color-lookup time, `JBColor.findPatternMatch` reads `UIManager.get("*")` and, for a requested name with no
  direct entry, scans the pattern map for an entry whose key the requested name **ends with**, returning the first
  `Color` match (results cached in `"*cache"`, reset on LaF change).

Gotchas with `*`:

- It is **suffix matching, not glob.** `*.borderColor` matches any key ending in `.borderColor`. There is no `Button.*`
  or mid-string wildcard. The `*` is literally just "match by the part after it."
- **Specificity is by presence/order, not by length — and the eager and lazy paths differ.** The surprising part: at
  *load time* the eager pass **overwrites** an already-present specific key with the pattern value, so `*` can beat a
  more specific key. At *runtime lookup*, the opposite holds — `JBColor` tries the direct key first and only falls back
  to the pattern when the direct key is absent. Reconciling the two: whichever value ends up stored under the specific
  key after the load-time passes is what a direct lookup returns, and that outcome is **iteration-order dependent** (a
  specific `ui` entry processed after the `*.` entry overwrites the pattern; one processed before is overwritten by it).
  Among patterns there is no longest-match arbitration — the eager pass overwrites in iteration order, and the lazy
  lookup returns the *first* ending-match. Two overlapping `*.` patterns, or a `*.` pattern plus a specific key for the
  same suffix, are an ambiguity to avoid.
- **It only covers *colors* at lookup-time fallback.** `findPatternMatch` returns only `Color` values; a `*.something`
  pattern with a non-color value participates in the eager pass but not the lazy color fallback.
- Some keys are deliberately **excluded from `*`**: the loader force-sets, e.g.,
  `ToolWindow.Button.selectedBackground/Foreground` defaults precisely so they are not left to a `*` pattern (
  `putDefaultsIfAbsent`). Don't assume a `*` rule reaches every component.

## Flattening (nested objects → dotted keys)

Only the `ui` section is flattened by the loader (`readFlatMapFromJson`); `icons`, `background`, and
`emptyFrameBackground` use `readMapFromJson`, which **preserves** their nested maps. For `ui`,
`{"Editor": {"SearchField": {"borderInsets": "7,10,7,8"}}}` becomes the single key `Editor.SearchField.borderInsets`.
Consequences:

- The **effective key is the dotted path**, so the same key can be written nested or flat, and they collide/override
  identically. This is intentional (lets a child override a parent key regardless of which form each used).
- **A path segment named `UI` is glued on without a dot.** Normally each nesting level adds a `.` separator, but a
  segment named exactly `UI` is concatenated to the preceding one with no dot (`putEntry`). So `{"Tree": {"UI": "..."}}`
  becomes `TreeUI`, not `Tree.UI` (and `{"Button": {"UI": "..."}}` becomes `ButtonUI`). The reason: these are Swing's
  *UI-delegate* keys, which map a component to its `ComponentUI` class, and Swing's own naming for them is the glued
  `<Component>UI` form (`TreeUI`, `ButtonUI`, `ComboBoxUI`). The nested JSON is a convenience; the loader joins it back
  into the key Swing actually looks up. Practical upshot: don't expect a `Tree.UI` key to exist — it's `TreeUI` — and
  this only applies to the literal segment `UI`.

## Per-OS values

Any leaf can be an object of OS variants instead of a scalar (`readFlatMapFromJson` + `putEntry`):

```json
"Menu.borderColor": {"os.default": "Grey12", "os.windows": "Blue12"}
```

Resolved at load time to the current OS (`os.mac`/`os.windows`/`os.linux`), falling back to `os.default`. The
non-matching OS entries are dropped; only the resolved scalar survives into `UIDefaults`. So you cannot observe the
other-OS values at runtime, and a key that only specifies, say, `os.windows` with no `os.default` resolves to
**nothing** on macOS/Linux.

## Value parsing — the key *suffix* picks the type

`ui` string values are parsed by **what the key name ends with** (`parseStringValue`/`parseUiThemeValue`), after
stripping a trailing `.compact`:

- ends with `Insets`/`.insets`/`padding` → parsed as insets `"top,left,bottom,right"`.
- ends with `Size`/`.size` → parsed as a `Dimension` `"w,h"`.
- ends with `Border`/`border` → parsed as a border (insets, a custom-line color+widths, or a border *class name* to
  instantiate).
- ends with `Width`/`Height` → number.
- ends with `UI` → left as a string (a UI-delegate class name), never number/color-parsed.
- starts with `AllIcons.` → a lazy reflective icon reference.
- ends with `.png`/`.svg` → a lazy image load.
- ends with `grayFilter` → a `GrayFilter` from three numbers.
- a `#`-prefixed string (len ≤ 9) → a color.
- otherwise → string, with a couple of **lenient warnings**: a numeric string on a non-dimension key is accepted as a
  number *with a warning*; a color-looking value missing its `#` is accepted as a color *with a warning* ("has color
  value but doesn't have # prefix"). These warnings are the loader telling you the file is slightly malformed even
  though it "worked."

Gotchas:

- **Type is inferred from the key name, not the value.** A perfectly valid color string on a key ending in `Size` will
  be parsed as a (failing) dimension, not a color. Naming matters.
- `isColorLike` requires a leading `#` *and* length ≤ 9, so `#RRGGBBAA` (8 hex + `#`) is the longest accepted color;
  anything longer is treated as a string.
- A `#`-prefixed value that fails to parse falls through to a **string** (with a warning), not an error — easy to miss.

## Practical review checklist

- A theme override that "doesn't apply": check whether a **direct key** is shadowing it, or whether it was written under
  the wrong section (a `colors` entry expecting to act as a LaF key, or a LaF override placed in `colors`).
- A value that renders **transparent/invisible**: likely a `colors` alias to an undefined name (resolves to
  `Gray.TRANSPARENT` with only a warning).
- A `*.foo` rule that seems to miss a component: it only fills keys that are absent at lookup time (lazy) or present at
  apply time (eager), returns only colors in the lazy path, and loses to any direct key. Some keys are intentionally
  pinned out of `*`.
- An inherited value you didn't expect: parent import is parent-first/child-overrides and transitive — trace the whole
  `parentTheme` chain, not just the leaf file. The live post-merge result is visible in the IDE via *Edit LaF
  Defaults* (filter by key).
- A per-OS key with no `os.default`: it resolves to nothing on the unlisted OSes.
- A dimension/insets/border that won't parse: confirm the **key suffix** matches the type you intend; the parser
  dispatches on the name, not the value.
