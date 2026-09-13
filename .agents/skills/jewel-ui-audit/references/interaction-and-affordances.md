# Interaction and Affordances

Use when reviewing how controls *behave*: hover/press feedback, focus handling, keyboard operability, accessibility,
and the standard IDE affordances (tooltips, context menus). For selection semantics, list mechanics, and text-selection
scope, see `selection-and-lists.md`.

## Quick triggers

- **Custom clickable/focusable component:** require visible themed focus (`focusOutline` where applicable), keyboard
  reachability, and Enter/Space activation.
- **Hover/lift/scale on static content:** flag false affordance unless the element is genuinely interactive and the
  effect matches the equivalent IDE control. In bridge mode, manually hovered plain rows/labels are usually wrong;
  remember the real hover exceptions are tabs, scrollbars, and icon buttons.
- **Icon-only control:** require a short factual tooltip naming the action and, where useful, shortcut; for
  `contentDescription` triage, use `accessibility-semantics.md`.
- **Truncated text:** require a tooltip exposing the full value.
- **Disabled submit as validation:** if the only feedback is a disabled button or vague tooltip, route to the validation
  trigger in `async-and-states.md`: field-level inline error messages and themed error outline/text are required.
- **Right-click expected:** selectable text needs the standard text context menu; actionable rows/items should expose
  the expected item menu rather than ignoring right-click.

## Affordance, focus, keyboard, and accessibility

Interactive UI is judged on more than its visuals: honest feedback, visible focus, and keyboard/screen-reader
operability are what make a control usable. Most of this is just **good UX** and applies everywhere Jewel runs; in the
IJPL/bridge context there's an added obligation to **fit in** — behave the way the surrounding IDE surfaces behave, so
the UI is predictable rather than novel. Review interaction on all these axes, not just polish.

The overarching rule: **prefer interaction patterns that already exist in the Int UI design system and avoid ones that
don't.** Int UI has an established vocabulary of interactions (selection, hover/press on the controls that have it,
focus rings, context menus, speed search, drag/reorder where supported); reach for those rather than importing a
gesture or affordance from another design language (Material ripples, swipe-to-dismiss, long-press menus, FAB-style
actions, elevation-on-hover). A pattern the user has never seen elsewhere in the IDE is a cost even when it's
well-built. When judging whether a given treatment or component belongs in Int UI at all — versus a justified custom
exception — use `idiomatic-components.md`; this file covers how the *idiomatic* interactions should behave once chosen.

### Feedback must match real capability

Any treatment that reads as "this responds to me" — hover highlight, lift/elevation, scale/zoom, pointer/cursor change
— sets an expectation; on an element that does nothing when acted on, it is a false affordance the user learns to
distrust.

- **Match feedback to behavior.** A non-clickable element should not gain actionable-looking feedback. Lift/elevation
  reads as "floats/is actionable"; scale/zoom reads as "will open/navigate" (on the web, image/card zoom usually signals
  a link). Reserve each for elements that actually do that.
- **Don't stack cues** — two "moving up in z" treatments on one hover (e.g., card elevation *and* image zoom together)
  is doubly misleading; pick at most one, and only for a genuinely interactive element.
- **Keep the pointer honest** — arrow for non-editable/non-selectable surfaces (not a text I-beam), no hand cursor on
  non-clickable elements. Hand cursor is usually only used for links; it is not a crutch to signify clickability on
  non-clickable-looking components.
- **Mind the design language** — elevation/shadow is a Material concept and foreign regardless of interactivity
  (see `idiomatic-components.md`); express state with border/fill/selection deltas.

### Hover/pressed is sparse by default — and Swing-compat mode suppresses it in the bridge

Don't assume every interactive element should change on hover. Many components that *support* hover in a standalone
app route their `hovered`/`pressed` appearance through **`swingCompatMode`**, which suppresses those visual states to
match IJPL's flatter behavior — and it is **on by default in the bridge** (`SwingBridgeTheme` sets
`swingCompatMode = true`; `FocusableComponentState`, and components like `Button`/`Checkbox`/`Link`, gate
`pressed`/`hovered` behind `!isSwingCompatMode`). So in a plugin those controls will *not* show hover in the IDE, by
design.

But it is **not** a blanket suppressor. Some IJPL controls legitimately hover in the bridge too, because they don't
gate on the flag: **tabs, scrollbars, and icon buttons** show hover/pressed regardless of `swingCompatMode` (to match
the Swing components' behavior in IJPL). So the rule isn't "no pressed/hover in the bridge" — it's "match what the
equivalent platform control does." Practical consequences:

- In the **bridge**, matching IJPL usually means *less* hover — adding a hover highlight to something the platform
  leaves inert (plain labels, static rows, most containers) makes it stand out rather than fit in. This is the common
  finding for hand-rolled `hoverable`/`collectIsHoveredAsState` rows. But do keep hover on the controls that platform
  side *do* hover: tabs, scrollbars, and toolbar/icon buttons. When in doubt, mirror the built-in.
- In **standalone**, hover/pressed states show (compat mode off); the honest-feedback rules above still apply, it's
  just a UX judgment rather than an IJPL-matching one.
- When a built-in already owns the right behavior, reuse it — it handles `swingCompatMode` (gating or not) correctly for
  you. Re-deriving hover from `collectIsHoveredAsState` by hand ignores compat mode and will over-animate in the bridge
  for the controls that are supposed to stay flat.

### Focus must be handled *and* visibly signaled

Every focusable element must show where the focus is — this is a core UX in both contexts, and doubly so in an IDE.
IntelliJ signals focus with a **focus ring**, and Jewel provides `Modifier.focusOutline(state, shape)` (plus a
`showOutline: Boolean` overload) which draws the themed `JewelTheme.globalColors.outlines.focused` ring. Use it rather
than a hand-rolled border or, worse, no indicator. Flag:

- A focusable/interactive custom component with **no visible focus indicator** (focus lands on it but the user can't
  see where they are).
- A focus ring drawn with a hardcoded color/shape instead of `focusOutline`/the themed outline colors.
- A focus ring that results in a visual border thicker than the focusOutline width (2.dp by default). The focus ring is
  usually drawn on top of the component's border (use the alignment parameter wisely). However, if the component has the
  same color as the focus ring, then there must be a 1.dp gap between the component and the focus ring. See the default
  button variant.
- Focus that can't *land* on an element that should be reachable (see keyboard, below).

### Keyboard operability and accessibility — non-negotiable and linked

Every actionable element must be operable without a mouse: reachable by Tab (or arrow keys within a composite like a
list/tree), activatable by Enter/Space, and dismissible by Esc where a popup/overlay is involved. This matters
everywhere, and an IDE audience especially expects full keyboard control. Keyboard operability and accessibility are two
views of the same requirement — the focus order, focusability, and activation that make a control keyboard-usable are
exactly what a screen reader relies on to expose and operate it; skip one, and you've usually broken both.

This skill judges the *interaction affordance* (is focus handled, signaled, keyboard-operable); the deep accessibility
review — accessible names/roles/states, screen-reader semantics, contrast — belongs to the `ui-accessibility` skill.
Raise the interaction-level gaps here and route the semantics depth there; a finding can legitimately belong to both.

For `Icon`/`Image` `contentDescription` checks, use `accessibility-semantics.md` and route full semantics validation to
`ui-accessibility`.

Keyboard shortcuts are also widely useful and particularly important in the IDE context. In general, you want to have at
least esc wired to dismissing modals/popups, and enter or ctrl/cmd-enter to confirm/send. Additional shortcuts should be
provided when possible, fitting in with the IDE's keymap and their philosophy.

Audit *any* interactive element for honest feedback, sparse-by-default hover, visible focus, and keyboard/a11y
operability.

## Tooltips: expected on icon-only and truncated content

Tooltips are a standard IDE affordance, not a nicety. Jewel provides a `Tooltip(tooltip = { ... }) { content }`
component (themeable via `TooltipStyle`/`LocalTooltipStyle`, defaulting to `JewelTheme.tooltipStyle`); use it rather
than a hand-rolled hover popup. Flag their **absence** where an IDE user expects them:

- **Icon-only controls** (toolbar buttons, icon actions with no visible label) need a tooltip naming the action —
  otherwise the control is unidentifiable until clicked. This is also an accessibility concern (overlaps
  `ui-accessibility`); raise the UX affordance here.
- **Truncated/ellipsized text** (list rows, tabs, breadcrumbs, tags) should expose the full value on hover; a clipped
  label with no tooltip hides information with no recovery.
- Keep tooltip content short and factual (action name, shortcut, full value). A tooltip that just repeats a visible
  label adds nothing; a tooltip carrying the keyboard shortcut is idiomatic in IntelliJ.

Do not attach tooltips to elements that have no meaning on hover, and do not use a tooltip as a substitute for a visible
label on a primary control.

## Context menus: right-click affordances and text menus

Right-click context menus are a core IDE interaction. Two cases to check:

- **Selectable/read-only text** should get the standard text context menu (copy, and select-all where relevant). Jewel
  supplies a public `TextContextMenu` object (which internally delegates to Compose's `TextContextMenuArea`), wired
  through the Jewel theme; prefer it over a bespoke right-click popup so entries, shortcuts, and theming match the IDE.
  A selectable text surface with **no** context menu (right-click does nothing) is a missing affordance. (Text-selection
  scope itself is covered in `selection-and-lists.md`.)
- **Actionable rows/items** (list entries, tree nodes, cards representing objects) usually warrant a context menu of the
  same actions available elsewhere. Build it from Jewel's menu/popup facilities (the public menu components), not a
  hand-rolled dropdown, so keyboard navigation, submenus, and styling are correct. Note Jewel's internal `ContextMenu`
  is not a public authoring entry point — go through the supported menu/popup and `TextContextMenu` APIs.

Flag: a surface where right-click is the expected way to reach actions (or copy text) but is unhandled; and a
hand-rolled menu that duplicates a Jewel menu built-in (route the built-in-vs.-custom reasoning to
`idiomatic-components.md`).

## Reporting

- For a **non-Int-UI interaction pattern** (a gesture/affordance imported from another design language), name the
  foreign pattern, the Int UI-native equivalent to use instead, and route the built-in-vs.-custom judgment to
  `idiomatic-components.md`.
- For false-affordance findings, state what the element does on click (usually nothing) and recommend removing the
  feedback.
- For hover findings in the bridge, note whether the platform equivalent hovers at all and that `swingCompatMode`
  suppresses hover/pressed by default. If the code manually hovers a plain row/label/container, state that plain IJPL
  rows/labels generally do not hover-highlight and the custom hover makes the plugin stand out. **Always include the
  exception clause in the finding**: tabs, scrollbars, and icon buttons do hover in the bridge even under
  `swingCompatMode`, but a plain row is not one of those exceptions. Recommend matching the built-in (or no hover) over
  a hand-rolled hover.
- For focus findings, name the missing/hardcoded indicator and recommend `Modifier.focusOutline(...)` with the themed
  outline colors.
- For keyboard/focus/a11y findings, state which interaction can't be done without a mouse (reach, activate, dismiss),
  then use `accessibility-semantics.md` for the handoff: full accessible-name/role/state/focus-order verification
  belongs to `ui-accessibility`.
- For `contentDescription` findings, use `accessibility-semantics.md`.
- For tooltip/context-menu findings, name the missing affordance and the Jewel API to use (`Tooltip`, `TextContextMenu`,
  the menu/popup components).
