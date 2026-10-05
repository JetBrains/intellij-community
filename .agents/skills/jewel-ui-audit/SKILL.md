---
name: jewel-ui-audit
description: Review Jewel/Compose Desktop UI for IntelliJ IDEs across UX, theming, layout, states, and bridge API use.
---

# Jewel UI Design Review

Review Compose UI built with JetBrains Jewel for two intertwined concerns:

1. **UX quality** — is every visual value themeable, is each custom component or treatment justified, does interaction
   behavior match what IDE users expect, and are the expected states present?
2. **Jewel/Compose API correctness** — is the code using Jewel (and, in plugins, the Swing/LaF bridge) APIs correctly
   and in the right way for its runtime context?

This is a UX-and-API-usage review, not an architecture, runtime-performance, or pure-accessibility review (route those
elsewhere; see "When to use").

## Runtime context: standalone vs. IJPL bridge (establish this first)

Jewel runs in two contexts, and the *correct* answer to many findings depends on which one applies. Determine it before
reviewing, because it changes the right fix, not just the wording:

- **Standalone Jewel** (a Compose Desktop app using Jewel): theming comes from the Int UI standalone styling and
  palette; there is no IJ Swing LaF to replicate.
- **IJPL plugin/Swing-LaF bridge** (Jewel UI embedded in an IntelliJ-based IDE): theming bridges the IDE's Swing
  Look-and-Feel. Colors should resolve through the bridge (e.g., `retrieveColorOrUnspecified("SomeKey")` /
  `retrieveColorOrNull` against IDE LaF keys), the bridge theme/styling providers are used, and the surface must track
  IDE theme switches and LaF changes.

Using standalone-only assumptions in a plugin (or vice versa) is itself a finding. When the context is not obvious from
imports/dependencies, say which you assumed.

Within the IDE/bridge context there are three theme generations: **old UI** (pre-Int UI, e.g., Darcula/IntelliJ —
effectively deprecated, not a design target, missing many keys), **New UI = Int UI** (the default target), and
**Islands** (Int UI-based, default in recent IJPL, renders tool windows as rounded islands). Detect via
`NewUI.isEnabled`/`JewelTheme.newUiChecker.isNewUi()`; Islands has no Jewel-native check yet (IntelliJ Platform offers
`IslandsState.isEnabled`, and only possible under New UI). For the full distinction, the API-misuse checklist, the
LaF-fallback rules, and the theming-container pattern, read `references/standalone-vs-bridge.md`.

Three hard rules when a plugin reads values from the LaF, because they cause real breakage in untested themes and when
the same UI runs standalone (e.g., in UI unit tests, where no IJPL LaF exists at all):

- **Always supply a fallback for everything read from the LaF**, not just colors — dimensions, insets, corner sizes,
  ints. Not every theme has every key (old 1p themes have no Int UI palette; many 3p themes lack keys), the read returns
  null/`Unspecified` when missing, and in standalone there is no IJ LaF at all. Prefer a layered fallback: primary key,
  then a palette/secondary key, then a hardcoded default.
- **Always supply both a light and a dark value** — palette indices mean different things in each mode, so one value
  cannot serve both. Jewel surfaces always need to support both light and dark variants.
- **Centralize this in a theming-container object** rather than scattering raw `retrieve*` calls through composables;
  standalone code can usually just hardcode the defaults in it. See `references/standalone-vs-bridge.md`. For how to
  structure a surface so the same content composables run in both a plugin and standalone (previews/UI tests) — the
  content-vs.-wiring split and injected theming — see `references/portability-structuring.md`.

This skill is read/evidence-based. Search hits are leads; read the surrounding code and confirm the concern is real and
user-visible (or a real API misuse) before reporting.

## Evidence-first review loop

Before reporting a finding:

1. Run a quick recon pass: determine runtime context (standalone vs. bridge), theme generation/keys involved, component
   families, and which reference files are relevant.
2. If the harness supports subagents, split independent review areas (theme/LaF, icons, interaction/a11y, layout/state)
   after recon; otherwise write a short recon plan and execute the relevant areas yourself.
3. Identify the exact component/API/pattern in the snippet.
4. Cite or name the line/pattern that proves the concern is present.
5. Apply only the relevant checklist/reference; do not run a full design-system audit on unrelated parsing, model, or
   business logic.
6. Do not infer that a fix exists elsewhere unless the prompt or visible code shows it.
7. Do not praise a fix unless the exact API/value is present in the snippet.

For the first-pass pattern cards, read `references/quick-triggers.md`. Use those triggers to decide which detailed
reference to open next; they are not a substitute for reading the actual code.

## Authoritative reference

The upstream source for IntelliJ UI conventions is the JetBrains UI
Guidelines: <https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html> (colors, layout/spacing, controls,
text, and component behavior). It is written for Swing UI, but the design principles — flat Int UI visual language,
theme-driven colors, consistent spacing, standard controls, restraint with motion/effects — apply equally to
Jewel/Compose surfaces, which are meant to match the same IDE. Treat the guidelines as the design intent and this skill
as how to check Jewel/Compose code against it. When a finding rests on an IDE convention (separator weight, control
sizing, spacing rhythm, color roles), the guidelines are the citable authority; consult them when a reviewer pushes back
on "is this actually the convention?" Always prefer existing patterns to novel ones unless there is a strong reason to
diverge.

## When to use

Use when authoring or reviewing Jewel/Compose Desktop UI that ships inside an IntelliJ-based IDE (platform, product,
or plugin), and the question is whether the visuals and interactions are themeable and idiomatic.

Defer, or hand off, when the primary concern is:

- screen readers, keyboard operability, focus order, accessible names/roles → use the `ui-accessibility` skill (this
  skill references it but does not replace it).
- Compose composition/state/effect architecture, recomposition, lazy-list keys, runtime performance → use the
  `compose-ui-audit` skill.
- which Jewel component or theme API to use when authoring from scratch → use the `jewel-ui` skill for the catalog; use
  this skill to judge whether an existing choice is idiomatic.

These overlap. It is fine to raise a finding that also belongs to another skill; say so and keep the design-system
reasoning here.

## The core questions

Apply these to every styled element, custom component, and interaction. They are the core of the skill.

1. **Is this themeable, or is it hardcoded?** Every color, and ideally every dimension and shape, should resolve to a
   theme source appropriate to the runtime context (standalone: Jewel global color/Int UI palette entry; bridge: an
   IDE LaF key via `retrieveColor*`; or a value derived from one) so it responds to theme switches, custom themes,
   high contrast, and IDE zoom. A literal `Color(...)`/`Color.X` or a bare `dp` magic number is a lead.
2. **Is the semantic role correct?** A token's role noun must match how it is used. A border color must not fill a
   background; a foreground/text color must not paint a container; a disabled color must not style enabled content.
3. **Is the Jewel/bridge API used correctly for this context?** The right styling provider, theme entry point, and color
   source differ between standalone and bridge (see `references/standalone-vs-bridge.md`). Reaching around Jewel to raw
   Swing/`UIManager`/`JBColor` from Compose, or using standalone palette entries in a plugin that should read LaF keys,
   is an API-usage finding even when it happens to look right in one theme.
4. **Is this custom treatment justified, and is there a built-in instead?** Before accepting a bespoke component or
   visual treatment, check for a Jewel built-in (`Divider`, `SimpleListItem`, `SingleSelectionLazyColumn`/
   `MultiSelectionLazyColumn`, group headers, standard scrollbars) and prefer it. A custom treatment needs a concrete
   reason the built-in cannot meet.
5. **Does the treatment match Int UI conventions and user expectations?** IntelliJ's language is flat and restrained.
   Material concepts (elevation/drop shadows, ripples, FABs) are foreign. Interaction affordances (hover lift, zoom,
   pointer changes) must be justifiable and signal real behavior, not decoration.

## Review areas

### Theming and color fidelity

Start with `references/quick-triggers.md` for palette-index, custom-key, and bridge-read trigger cards. Then read
`references/theming-and-color.md` when you find color or dimension literals, or suspect a wrong color role. In the
bridge/plugin context, read `references/bridge-laf-read-api.md` for the exact LaF read-helper catalog and the
safe-vs.-raw fallback forms to require. When the surface touches editor/syntax content (code blocks, diff, editor-like
panes), reads from `EditorColorsManager`/`EditorColorsScheme`, captures `JBColor` into Compose colors, or caches theme
values across theme/scheme switches, read `references/ijpl-theming-and-editor-scheme.md` — the UI LaF and the editor
color scheme are two independent systems with separate change events. When reviewing or reasoning about a `*.theme.json`
itself — why a key resolves (or silently doesn't), parent inheritance, the `*` wildcard, per-OS values, nested-key
flattening, or named-color aliases — read `references/theme-key-resolution.md` for how the loader turns the file into
`UIDefaults`.

Flag:

- Literal colors in UI: `Color(0x...)`, `Color.Red`, `Color.DarkGray`, etc. Resolve to a Jewel global color, an Int UI
  palette entry, or a Swing LaF key instead. Treat replacing a previously themed value with a literal as a strong red
  flag — especially when the original themed value is still visible (e.g., commented out next to the literal).
- Semantic role mismatches. The worst common case is a `borders.*` token used as a `.background(...)` fill: border
  tokens are tuned for 1 px separators and diverge from panel/background tokens under custom themes. Use a content/panel
  background role.
- Magic-number dimensions and shapes (`RoundedCornerShape(8.dp)`, paddings, sizes) repeated across files with no shared
  token, and with no verification of how they respond to IDE zoom/density (Int UI/Islands also expose `.compact` density
  variants).
- Raw palette assumptions: referencing a palette entry directly instead of a semantic role, or assuming a palette index
  maps consistently across themes (it does not — old/Int UI/Islands number differently, and even reverse direction). The
  palette is not semantic; components must use role tokens.
- Any LaF read (color, dp, insets, arc) with no fallback, or a single color used for both light and dark (the hard rules
  above).

Do not over-correct: a value **derived** from the theme is correct even if it contains a literal. For example, a size
computed as an offset/scale of `JewelTheme.defaultTextStyle.fontSize` scales with IDE font settings and is the *model*
to follow, not a defect. The rule is "themeable by derivation," not "no numbers anywhere." (Mechanism note: Jewel
typography and platform `JBFont` derive sizes additively via `biggerOn`/`lessOn`, not by a fixed multiplier — don't
assert a specific ratio as the type scale; the point is derivation from the theme base.)

### Idiomatic components and Int UI conventions

Read `references/idiomatic-components.md` for the *built-in vs custom* decision and the Int UI concept boundaries.

Flag:

- Material concepts in an Int UI surface: `shadowElevation`/drop shadows via `graphicsLayer`, Material ripples,
  FAB-like floating buttons. Express grouping and state with borders, background-fill deltas, and selection instead.
- Custom components that duplicate a Jewel built-in (hand-rolled dividers, list items, selectable lists, scrollbars).
  Heavy non-standard treatments (e.g., a 4.dp divider where IDE separators are 1.dp) need explicit justification.
- Decorative-only complexity: a bespoke component or effect that adds no capability a built-in lacks.
- Editor/window registration policy treated as plumbing. For a `FileEditor`/tool-window surface, check `getPolicy()` and
  similar: `FileEditorPolicy.HIDE_OTHER_EDITORS` (or anything that seizes the frame or forces focus) is a UX takeover
  that is wrong for a non-modal informational screen. This lives in a provider/factory file, not the composable, so look
  for it deliberately.
- Icons loaded by hand-built path or `Image(painterResource(...))` instead of an `IconKey` + the `Icon`
  composable/painter provider — this loses New-UI path mapping, dark variants, and theme recoloring. For hardcoded
  filename variants, name the exact path divergence: Swing `IconLoader` may look for `@2x.svg`, but Jewel's `HiDpi`
  hint does not apply `@2x` to SVGs; and a Jewel load failure is magenta while the classic Swing path often fails
  blank/silent. For stateful Jewel icons, explicitly state that specialized variants such as `Selected`/`Disabled`/
  `_dark` are optional fallbacks, but the bare base icon must exist because Jewel falls through to it at the end of the
  hint branch.
- **Cross-plugin/cross-module icon references that resolve against the wrong classloader** — icons are bound to a
  `ClassLoader`, and referencing another plugin's icon class/resource often works in a from-sources run but breaks
  silently in the packaged/release build (isolated per-plugin classloaders). "It shows up running from sources" is not
  evidence it ships correctly. When code loads, bridges, or builds icons, read `references/icon-loading.md` (Jewel
  `IconKey`/`PainterHint`, classic `AllIcons`/`IconLoader`, and the classloader/classpath traps). For the separate
  experimental `com.intellij.platform.icons` framework, read `references/icon-loading-experimental.md`: do not call it
  unbundled; the concern is `@Internal`/`@Experimental` API stability.

### Interaction semantics

Start with `references/quick-triggers.md` for validation, text-selection, focus, and list trigger cards. Then read
`references/interaction-and-affordances.md` for hover/focus/keyboard, tooltip, and context-menu patterns,
`references/accessibility-semantics.md` for `contentDescription` and focus/keyboard semantics handoff, and
`references/selection-and-lists.md` for selectable lists, text-selection scope, and scroll-derived selection.

Flag:

- Hover/elevation/zoom on **non-interactive** elements. Lift, scale, and z-raise read as "clickable/activatable";
  applying them to a non-clickable card or image communicates a false affordance. Stacking two "z-axis up" effects
  (e.g., elevation increase and zoom on the same hover) is doubly wrong.
- Hand-rolled hover in an IJPL bridge row/plain label/container. In bridge mode, `swingCompatMode` suppresses
  hover/pressed for many controls and plain rows generally stay flat; custom `hoverable`/`collectIsHoveredAsState`
  highlights make the plugin stand out. Always name the important exceptions so the rule is not over-applied: tabs,
  scrollbars, and icon buttons do hover in the bridge; a plain row is not one of them.
- Selectable lists built as a `Column`/`LazyColumn` with manual `selected = ...` + `.clickable { }` instead of
  `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` (the modern replacements for deprecated `SelectableLazyColumn`,
  usually with `SimpleListItem` rows). `SimpleListItem` itself is normally the right row component; the smell is
  hand-rolling the selection container. The built-in owns selection state, keyboard navigation (arrows, home/end), and
  focus; evaluate missing speed search/type-to-filter separately for any non-trivial list. In a justified fork that
  reuses `SimpleListItem` for the row body, explicitly preserve that reuse in **Justified as-is** instead of
  recommending raw `Text`/custom row replacement.
- Non-trivial or searchable lists/trees/combo boxes with **no speed search** (type-to-filter). Speed search should be
  the default IDE affordance unless there is a good alternative such as a visible type-to-filter/search field, and it is
  *not* built into the selection column — use `SpeedSearchableLazyColumn`/`SpeedSearchArea` (with `SpeedSearchableTree`/
  `SpeedSearchableComboBox` siblings). See `references/selection-and-lists.md`.
- Selection or "active item" derived from scroll position (e.g., `items[lazyListState.firstVisibleItemIndex]`). This is
  structurally unable to select trailing items: once scrolled to the end, the last item sits at the bottom of the
  viewport while an earlier item still owns the top, so it never becomes "first visible." A correct active-section model
  special-cases end-of-scroll and keys off a threshold crossing.
- Over-broad text selection on a read-only surface (whole-region `SelectionContainer` or `selectable = true` over a
  composite layout). It shows the text I-beam cursor over layouts, images, and padding that are not text, and lets
  selection bleed across discrete layouts. Scope selection to the text runs where copying is a real use case, or leave a
  read-only surface non-selectable so the arrow pointer is shown. For UI text which is not in a text field, even if it
  should be selectable (e.g., error message in a label), it should avoid showing the I-Beam anyway. When controls are
  nested inside selectable content, contrast existing protected controls with unprotected siblings: e.g., "the Install
  button already uses `DisableSelection`, but the More `IconButton` does not." Every nested action control needs its own
  selection-disabling/arrow-pointer handling.
- Missing tooltips where an IDE user expects them: icon-only controls (toolbar/icon buttons with no visible label) and
  truncated/ellipsized text (rows, tabs, breadcrumbs). Use Jewel's `Tooltip` component; keep content factual (action
  name, shortcut, full value). Overlaps `ui-accessibility` for icon buttons. See
  `references/interaction-and-affordances.md`.
- Missing or hand-rolled context menus: right-click unhandled on selectable text or on actionable rows/items where
  right-click is the expected path to actions/copy. Use Jewel's `TextContextMenu` for text and the built-in menu/popup
  components for item menus, not a bespoke dropdown (Jewel's internal `ContextMenu` is not a public entry point).
  See `references/interaction-and-affordances.md`.

### Spacing and layout

Start with `references/quick-triggers.md` for resizable-pane, prose-width, and raw-scroll trigger cards. Then read
`references/spacing-and-layout.md` for the additive-stacking analysis pattern.

Flag:

- Spacing owned at multiple nesting layers at once, so values **stack additively**. A gap between siblings should come
  from one source (the parent's `Arrangement.spacedBy`), not parent arrangement plus each child's outer padding plus a
  grandparent arrangement. Compute the effective gap by hand to expose the stack.
- Per-axis asymmetry produced by different mechanisms: e.g., uniform `padding(8.dp)` works horizontally but may stack to
  other spacings added vertically, yielding smaller horizontal margins inside oversized vertical margins. Compare
  effective horizontal vs. vertical spacing at each seam (inner padding, inter-item, inter-row). Do not assume all
  spaces must be the same; rather, question the visual pleasantness/intentionality of asymmetric margins. Use similar
  layouts and components in the rest of the codebase as a reference, try to promote consistency.
- Reusing a content-rhythm token (e.g., a Markdown `blockVerticalSpacing`) as a layout margin, so changing content
  density silently changes layout gutters. Keep content spacing and layout spacing as separate tokens.
- No shared spacing scale: 8/12/32 picked independently per file, so inner-vs.-gutter ratios are accidental.

### State completeness, async media, and expected-but-absent behavior

Read `references/async-and-states.md` for the required-states checklist and the absence checklist.

This area includes a class of finding the others do not: the **expected capability that is simply absent**. There is no
code line to grep, so you must check it deliberately against the feature's purpose (e.g., a feed feature with no "new
since last seen" tracking, a network dependency with no loading/offline story, a legitimately emptyable view with no
empty state). Run the absence checklist in `references/async-and-states.md` and report missing capabilities with their
user-facing consequence.

Flag:

- A composable gated behind async work that early-returns into blankness instead of rendering a Loading state. Any
  async-backed screen must define what renders during Loading and Error, not just the happy path.
- Async images that render nothing (or `null` into flow content) while loading: no reserved space causes layout shift as
  each image arrives; reserve a fixed-ratio placeholder. Provide a loading indicator/shimmer and an error affordance
  (alt text or broken-image state), not a silent permanent gap or vanishing into nothing.
- Network-fetched media on a screen whose other content is bundled/offline, with no offline story and log-only failure.
  Route remote media through the standard image-loading path rather than ad-hoc fetching.
- Accessibility-semantics triage (overlaps `ui-accessibility`): `contentDescription = ""`, noisy duplicate image
  descriptions, or icon-only controls/toggles without concise action/state descriptions. Use
  `references/accessibility-semantics.md`; full semantics verification belongs to `ui-accessibility`.
- Hardcoded UI strings in an IntelliJ plugin, instead of using bundles/messages.
- Progress feedback missing or the wrong kind: a perceptibly long operation with no indicator, or an indeterminate
  spinner where the total is known and a determinate percentage/step count is possible.
- Input surfaces (settings, dialogs, forms) with no inline validation — only a disabled or post-submit failure, no
  field-level message. The message should localize the failing field and use the themed error role/outline, not a
  hardcoded red.

## Output format

```markdown
## Summary

[overall: idiomatic and themeable/targeted fixes/significant rework]

## Findings

1. [severity] [finding] — `path:line`
    - Why it matters (theming/role/idiom/interaction/spacing/state)
    - Suggested fix (name the built-in, token, or pattern to use)
2. ...

## Justified as-is

[custom treatments that are appropriate, with the reason — so reviewers don't churn them]

## Confidence and limits

[what was checked; what needs runtime/zoom/theme-switch/human verification]
```

Always include a "Justified as-is" section when the code has custom components or non-default values that are
appropriate. The skill's value is as much in *not* flagging justified choices (and the themeable-by-derivation pattern)
as in catching the violations.

## Validation before finishing

- Each finding cites a concrete file/line and names the idiomatic replacement (built-in, token, or pattern), not just
  the problem.
- Themeable-by-derivation values are not flagged as hardcoded.
- Genuinely justified custom components are acknowledged, not reflexively flagged.
- Findings that are really accessibility, architecture, or runtime concerns are routed to the matching skill rather than
  re-litigated here.
- State explicitly which checks need manual verification (theme switch, high contrast, IDE zoom, screen reader).
