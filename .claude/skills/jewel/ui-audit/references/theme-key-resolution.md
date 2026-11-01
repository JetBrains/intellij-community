# Theme Key Resolution and `.theme.json` Gotchas

This reference explains how an IntelliJ `*.theme.json` becomes the `UIDefaults` Swing LaF map that the bridge reads.
Use it when investigating why a key resolves to an unexpected value, when a theme override does not apply, or when
reviewing a theme file.

It is the load-time companion to `theming-and-color.md` (role semantics) and `standalone-vs-bridge.md` (reading
`UIDefaults` back from Compose).

Everything here comes from the platform loader (`UITheme.kt`, `UIThemeBean.kt`, `uiThemeParser.kt`, `ColorMap.kt` in
`platform/platform-impl/src/com/intellij/ide/ui/`) and the runtime `*` fallback in `JBColor.java`
(`platform/util/ui/src/com/intellij/ui/`). Cite those files when a finding is questioned, and re-verify specific key
names against the target build.

## The top-level sections of a `.theme.json`

`readTheme` in `UIThemeBean.kt` parses a theme file into a fixed set of sections. Each section resolves through its own
code path, so placing a value in the wrong section produces unintended behavior:

- A LaF key placed under `colors` becomes a `ColorPalette.<name>` entry rather than overriding that LaF key.
- Unknown top-level *objects* log a warning and are dropped, while unknown scalar fields can be ignored silently.

The standard top-level sections are:

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
- **Top-level scalars** include `name`, `parentTheme`, `dark`, `editorScheme`, `author`, `nameKey`, and
  `resourceBundle`. The `editorScheme` field references a *separate* `.xml` editor color scheme because the UI theme
  and the editor scheme are two independent systems (see `ijpl-theming-and-editor-scheme.md`).

Gotcha: an `id` field in the JSON is **ignored with a warning** — the id comes from the `themeProvider` registration in
XML, not the file. A `UIDesigner` block is skipped entirely. Unknown top-level objects are warned and dropped.

## Resolution order (load time)

When `createTheme` loads a theme, it executes three steps in order:

1. **Parent import** (`importFromParentTheme`) — merges the `parentTheme` (or the default Light/Dark experimental
   parent when absent) across `ui`, `icons`, `background`, `colorMap`, and `iconColorOnSelectionMap`.
   - Merging is **parent-first, child-overrides**: the child starts from a copy of every parent entry, then overwrites
     by key, inheriting unmentioned keys transitively up the chain.
   - The nested `icons.ColorPalette` sub-map is also union-merged rather than replaced wholesale.
2. **Named-color resolution** (`initializeNamedColors`) — resolves direct `#hex` entries in `colors` and dereferences
   **name→name aliases** across two passes (supporting alias-of-alias).
   - An alias to an undefined name logs a warning and resolves to **`Gray.TRANSPARENT`**, producing an invisible value
     rather than throwing an error.
3. **Apply to UIDefaults** (`applyTheme`) — see next section.

Because parent import runs *before* named-color resolution, a child can reference a color name defined only in its
parent (and vice versa) against the merged maps.

## What lands in UIDefaults (`applyTheme`)

`applyTheme` writes entries into `UIDefaults` in two ways:

1. Every `colors` entry is written as **`ColorPalette.<name>`** (e.g., `gray-10` → `ColorPalette.gray-10`; classic
   `Gray1` → `ColorPalette.Gray1`). This is why the Jewel bridge reads palette colors under the `ColorPalette.` prefix,
   and why the Islands vs. classic key-format difference matters (see `theming-and-color.md`).
2. Every `ui` entry is written under its flattened key. If the value is a **string that matches a name in `colors`**, it
   resolves to that palette color; otherwise it is parsed by type (see "Value parsing").
   - For example, `"Panel.background": "gray-10"` tracks the palette color, whereas `"Panel.background": "#191A1C"` is
     an inline literal.

### The `*` wildcard key — suffix patterning

A `ui` key that starts with `*.` (e.g., `"*.borderColor"`, `"*.background"`) is a **suffix pattern**, applied two ways
at load time (`applyTheme` + `addPattern`):

- **Eager pass:** when the `*.` key is processed, every `UIDefaults` key **already present at that moment** whose name
  **ends with** the tail (`.borderColor`) is overwritten with the pattern's value.
  - Thus `*.borderColor` retroactively overrides existing `Button.borderColor` or `ComboBox.borderColor` entries
    inherited from parent/platform defaults or defined earlier in the same `ui` map.
  - A specific key written *later* in the same `ui` map runs after the pattern and overwrites it back.
- **Lazy map:** the pattern is also stored in a `"*"` map entry for keys that do not exist yet at apply time.
  - At color-lookup time, `JBColor.findPatternMatch` reads `UIManager.get("*")` when a requested key has no direct
    entry. It scans the pattern map for a suffix match and returns the first `Color` match (cached in `"*cache"` and
    reset on LaF change).

Gotchas with `*`:

- It is **suffix matching, not glob.** `*.borderColor` matches any key ending in `.borderColor`. There is no `Button.*`
  or mid-string wildcard. The `*` is literally just "match by the part after it."
- **Specificity follows presence and iteration order, not key length.** At *load time*, the eager pass **overwrites**
  already-present specific keys with the pattern value. At *runtime lookup*, `JBColor` checks the direct key first and
  falls back to the pattern only when the direct key is absent.
  - Which value survives under the specific key depends on **iteration order**: a specific `ui` entry processed after
    the `*.` entry overwrites the pattern, whereas one processed before is overwritten by it.
  - Among patterns there is no longest-match rule: the eager pass overwrites in iteration order, and lazy lookup returns
    the *first* suffix match. Avoid overlapping `*.` patterns or combining a `*.` pattern with a specific key for the
    same suffix.
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
- **A path segment named `UI` is concatenated without a dot.** While normal nesting adds a `.` separator, `putEntry`
  joins a literal `UI` segment directly to the preceding segment.
  - Thus `{"Tree": {"UI": "..."}}` becomes `TreeUI` (not `Tree.UI`), and `{"Button": {"UI": "..."}}` becomes
    `ButtonUI`.
  - These correspond to Swing's `<Component>UI` delegate keys (`TreeUI`, `ButtonUI`, `ComboBoxUI`), which map a
    component to its `ComponentUI` class. Do not look up `Tree.UI`; this concatenation rule applies only to the exact
    segment `UI`.

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
- otherwise → string, with two **lenient warnings**:
  - A numeric string on a non-dimension key is parsed as a number *with a warning*.
  - A color-like hex value missing its `#` is parsed as a color *with a warning* ("has color value but doesn't have #
    prefix"), signaling a malformed entry that still loaded.

Gotchas:

- **Type is inferred from the key name, not the value.** A perfectly valid color string on a key ending in `Size` will
  be parsed as a (failing) dimension, not a color. Naming matters.
- `isColorLike` requires a leading `#` *and* length ≤ 9, so `#RRGGBBAA` (8 hex + `#`) is the longest accepted color;
  anything longer is treated as a string.
- A `#`-prefixed value that fails to parse falls through to a **string** (with a warning), not an error — easy to miss.

## Practical review checklist

- **Theme override does not apply.** Check whether a **direct key** shadows it, or whether it sits in the wrong section
  (a `colors` entry expected to act as a LaF key, or a LaF override placed in `colors`).
- **Value renders transparent/invisible.** Likely a `colors` alias to an undefined name (resolves to `Gray.TRANSPARENT`
  with only a warning).
- **`*.foo` rule misses a component.** It fills only keys absent at lookup time (lazy) or present at apply time
  (eager). It returns only colors in the lazy path and loses to direct keys or keys pinned via `putDefaultsIfAbsent`.
- An inherited value you didn't expect: parent import is parent-first/child-overrides and transitive — trace the whole
  `parentTheme` chain, not just the leaf file. The live post-merge result is visible in the IDE via *Edit LaF
  Defaults* (filter by key).
- A per-OS key with no `os.default`: it resolves to nothing on the unlisted OSes.
- A dimension/insets/border that won't parse: confirm the **key suffix** matches the type you intend; the parser
  dispatches on the name, not the value.
