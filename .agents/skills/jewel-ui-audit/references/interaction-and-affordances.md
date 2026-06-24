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

Interactive UI requires honest feedback, visible focus, and keyboard/screen-reader operability. These UX requirements
apply everywhere Jewel runs; in the IJPL/bridge context, surfaces must also match surrounding IDE behavior so controls
remain predictable.

**Prefer interaction patterns from the Int UI design system and avoid foreign ones.** Use Int UI interactions
(selection, hover/press on supported controls, focus rings, context menus, speed search, drag/reorder) rather than
Material ripples, swipe-to-dismiss, long-press menus, FAB actions, or elevation-on-hover.

Novel patterns add friction even when well-built. Use `idiomatic-components.md` to judge whether a component or
treatment belongs in Int UI.

### Feedback must match real capability

Any treatment that signals interactivity (hover highlight, lift/elevation, scale/zoom, or pointer changes) sets an
expectation. Applying it to an inert element creates a false affordance.

- **Match feedback to behavior.** A non-clickable element should not gain actionable-looking feedback. Lift/elevation
  reads as "floats/is actionable"; scale/zoom reads as "will open/navigate" (on the web, image/card zoom usually signals
  a link). Reserve each for elements that actually do that.
- **Don't stack cues** — applying two z-axis treatments on one hover (such as card elevation and image zoom together) is
  doubly misleading. Pick at most one, and only for a genuinely interactive element.
- **Keep the pointer honest** — arrow for non-editable/non-selectable surfaces (not a text I-beam), no hand cursor on
  non-clickable elements. Hand cursor is usually only used for links; it is not a crutch to signify clickability on
  non-clickable-looking components.
- **Mind the design language** — elevation/shadow is a Material concept and foreign regardless of interactivity
  (see `idiomatic-components.md`); express state with border/fill/selection deltas.

### Hover/pressed is sparse by default — and Swing-compat mode suppresses it in the bridge

Not every interactive element changes on hover. In the bridge, `SwingBridgeTheme` sets `swingCompatMode = true` by
default, and `FocusableComponentState` (along with `Button`, `Checkbox`, and `Link`) gates `pressed`/`hovered` visuals
behind `!isSwingCompatMode` to match IJPL's flat behavior.

`swingCompatMode` is not a blanket suppressor. **Tabs, scrollbars, and icon buttons** do not gate on the flag and show
hover/pressed in the bridge to match their Swing counterparts. Match what the equivalent platform control does:

- In the **bridge**, adding hover highlights to inert platform elements (plain labels, static rows, most containers)
  makes a plugin stand out; flag hand-rolled `hoverable`/`collectIsHoveredAsState` rows. Keep hover on controls that
  hover in the platform: tabs, scrollbars, and toolbar/icon buttons.
- In **standalone**, hover/pressed states show (compat mode off); the honest-feedback rules above still apply, it's
  just a UX judgment rather than an IJPL-matching one.
- When a built-in already owns the right behavior, reuse it — it handles `swingCompatMode` (gating or not) correctly for
  you. Re-deriving hover from `collectIsHoveredAsState` by hand ignores compat mode and will over-animate in the bridge
  for the controls that are supposed to stay flat.

### Focus must be handled *and* visibly signaled

Every focusable element must show where focus is. IntelliJ signals focus with a **focus ring**, and Jewel provides
`Modifier.focusOutline(state, shape)` (plus a `showOutline: Boolean` overload) to draw the themed
`JewelTheme.globalColors.outlines.focused` ring. Use it instead of a hand-rolled border or missing indicator. Flag:

- A focusable/interactive custom component with **no visible focus indicator** (focus lands on it but the user can't
  see where they are).
- A focus ring drawn with a hardcoded color/shape instead of `focusOutline`/the themed outline colors.
- A focus ring that produces a visual border thicker than the default 2.dp `focusOutline` width. Draw the focus ring on
  top of the component's border via the alignment parameter. If the component shares the focus ring color, leave a 1.dp
  gap (as in the default button variant).
- Focus that can't *land* on an element that should be reachable (see keyboard, below).

### Keyboard operability and accessibility — non-negotiable and linked

Every actionable element must work without a mouse: reachable by Tab (or arrow keys inside a list/tree), activatable by
Enter/Space, and dismissible by Esc for popups/overlays. Keyboard operability and accessibility share the same
foundation: screen readers rely on the same focus order, focusability, and activation semantics as keyboard navigation.

This skill judges the *interaction affordance* (focus handling, visible ring, and keyboard operability). Route deep
accessibility verification (accessible names/roles/states, screen-reader semantics, contrast) to `ui-accessibility`.

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

- **Selectable/read-only text** needs the standard text context menu (copy and select-all). Use Jewel's public
  `TextContextMenu` (which delegates to Compose's `TextContextMenuArea` with Jewel theming) instead of a bespoke popup.
  Flag selectable text where right-click does nothing (see `selection-and-lists.md` for text-selection scope).
- **Actionable rows/items** (list entries, tree nodes, object cards) warrant a context menu exposing their actions.
  Build it from Jewel's public menu/popup components rather than a hand-rolled dropdown so keyboard navigation,
  submenus, and styling work (Jewel's internal `ContextMenu` is not a public authoring API).

Flag surfaces where right-click is expected for actions or copy but is unhandled. Also flag hand-rolled menus that
duplicate a Jewel menu built-in (route built-in-vs.-custom reasoning to `idiomatic-components.md`).

## Reporting

- For a **non-Int-UI interaction pattern** (a gesture/affordance imported from another design language), name the
  foreign pattern, the Int UI-native equivalent to use instead, and route the built-in-vs.-custom judgment to
  `idiomatic-components.md`.
- For false-affordance findings, state what the element does on click (usually nothing) and recommend removing the
  feedback.
- For hover findings in the bridge, note whether the platform equivalent hovers and that `swingCompatMode` suppresses
  hover/pressed by default:
  - If code manually hovers a plain row, label, or container, state that plain IJPL rows/labels stay flat and custom
    hover makes the plugin stand out.
  - **Always include the exception clause**: tabs, scrollbars, and icon buttons do hover in the bridge under
    `swingCompatMode`, but a plain row does not. Recommend matching the built-in (or no hover) over hand-rolled hover.
- For focus findings, name the missing/hardcoded indicator and recommend `Modifier.focusOutline(...)` with the themed
  outline colors.
- For keyboard/focus/a11y findings, state which interaction can't be done without a mouse (reach, activate, dismiss),
  then use `accessibility-semantics.md` for the handoff: full accessible-name/role/state/focus-order verification
  belongs to `ui-accessibility`.
- For `contentDescription` findings, use `accessibility-semantics.md`.
- For tooltip/context-menu findings, name the missing affordance and the Jewel API to use (`Tooltip`, `TextContextMenu`,
  the menu/popup components).
