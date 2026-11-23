# Shortcuts and keymaps

Jewel's shortcut subsystem (`org.jetbrains.jewel.foundation.shortcut`, experimental) gives Compose
content a first-class way to participate in keyboard dispatch in both hosts:

- **Commands** (`Modifier.shortcut`) are host-visible actions resolved through the host keymap — the
  IntelliJ keymap in the bridge, a `JewelKeymap` in standalone applications. They respect user keymap
  customisation and are the default choice.
- **Claims** (`Modifier.claimShortcut`, `Modifier.claimKeyEvent`) let a focused component own a specific
  physical keystroke *ahead of* keymap lookup. They are deliberately local and visible in source review:
  use them for editor-like surfaces that must own keys such as <kbd>Ctrl</kbd>+<kbd>Enter</kbd>, not as a
  convenience to skip registering commands.

Precedence, in both hosts: **open Jewel menu scope → focused claims (`claimShortcut`, then `claimKeyEvent`) → host keymap commands → ordinary Compose input.** An
unbound key is never consumed.

## Binding commands and claims

```kotlin
// The nearest focused enabled binding for an action wins; disabled bindings fall
// through outward; blocksOuterBindings stops the fall-through.
Box(Modifier.shortcut(SelectAllRows) { table.selectAll() }) {
    Editor(
        Modifier
            .shortcut(SelectAllRows) { editor.selectAllText() } // overrides while focused
            .claimShortcut(JewelKeySequence(JewelKeyStroke(Key.Enter, ctrl = true))) { submit() }
    )
}
```

Binding and claim modifiers observe focus like `Modifier.provideData` does: they see the focus of
modifiers applied **after** them in the chain and of descendant nodes, so they must come before
`focusable()` in the chain.

> [!WARNING]
> **Anti-pattern — a shortcut placed *after* `focusable()` is silently inert.** Because the modifier
> only observes focus of nodes that come after it, a binding or claim applied after `focusable()`
> never sees its own node gain focus, so it never registers with the resolver root. There is no
> runtime error: the control renders **disabled** (it samples `NoFocusedBinding`) and its handler
> **never fires**. This is the same ordering contract as `Modifier.provideData`.
>
> ```kotlin
> // WRONG: .shortcut after .focusable() — renders disabled, never invokes.
> Box(Modifier.focusable().shortcut(SelectAllRows) { table.selectAll() })
>
> // RIGHT: .shortcut before .focusable().
> Box(Modifier.shortcut(SelectAllRows) { table.selectAll() }.focusable())
> ```

## Standalone applications

One `JewelShortcutHostState` per window. Install its root modifier at the content root and pass its
handler to the window's **AWT-level** key hook — not a root `Modifier.onPreviewKeyEvent`, which never
sees KEY_TYPED events and therefore cannot stop a claimed printable key from inserting its character:

```kotlin
val keymap = InMemoryJewelKeymap.fromDefaults("app", actionRegistry)
val state = JewelShortcutHostState { keymapManager.activeKeymap.value }

Window(onPreviewKeyEvent = state::onPreviewKeyEvent, onCloseRequest = ...) {
    IntUiTheme {
        Box(state.resolverRootModifier) { AppContent() }
    }
}
```

- `JewelKeymap` supports named schemes, parent inheritance with `hideInherited` markers, prefix queries
  for two-stroke sequences, conflicts, and runtime mutation (`modificationCount` drives downstream menu hints).
  Persistence is application policy.
- **Popups and dialogs run in their own Compose scene layers and do not inherit window key hooks.**
  Jewel-owned focusable `Popup` components (including `PopupMenu`, `ContextMenu`, and `JBPopupRenderer`)
  automatically wrap their content in `state.resolverRootModifier` and thread `state::onPreviewKeyEvent`
  ahead of any caller handler when a `LocalJewelShortcutHost` is present, so shortcuts and claims declared
  inside a focusable popup register and dispatch without overriding the main window's bindings, while
  shadowing outer roots' claims so background surfaces cannot swallow popup keystrokes. Non-focusable
  popups (such as tooltips) do not attach a resolver root or reset engine state when dismissed. Standalone
  dialogs must install `state.resolverRootModifier` and pass `state::onPreviewKeyEvent` themselves. While
  a menu is open, its item shortcuts are absorbed into the host as a *menu scope* resolved first in
  dispatch, so menu-local and host dispatch can never race.

## IntelliJ bridge

`JewelComposePanel` wires the claim lane automatically:

- The panel wrapper implements `KeyboardAwareContainer`, the platform's ancestor-side escape
  hatch: the actual AWT focus owner inside a `ComposePanel` is an internal skiko component that cannot
  implement `KeyboardAwareFocusOwner` itself, so `IdeKeyEventDispatcher` consults the wrapper instead.
  A focused claim makes the IDE skip keymap processing for that keystroke while ordinary AWT dispatch
  continues.
- A `KeyEventDispatcher` scoped to the wrapper's focused descendants then delivers the claimed
  key-down to the claim handler and swallows the trailing KEY_TYPED.
- **Commands stay IntelliJ actions** resolved through the IDE keymap. Declare `JewelActionBridgeAction`
  in `plugin.xml` under the Jewel action ID with `<keyboard-shortcut>` entries for IDE-wide defaults;
  `JewelBridgeActionRegistry.register` can also register a runtime `JewelActionBridgeAction` (for
  programmatic or component-scoped actions), but runtime registration deliberately does not mutate
  `KeymapManager` — bind component-scoped shortcuts via `ActionUtil.wrap(id).registerCustomShortcutSet(...)`
  if you want dialog-local defaults without declaring them in `plugin.xml`. At runtime,
  `JewelActionBridgeAction` resolves the focused host from the data context (the wrapper snapshots its host
  state under a dedicated `DataKey`), re-resolves the nearest focused enabled binding at perform time, and
  implements `ActionPromoter.suppress()` so strictly outer competing Jewel actions yield to the innermost
  focused enabled binding without jumping ahead of platform `ActionPromoter` extensions (such as
  `SpeedSearchActionPromoter`). Residual scoping limitation: if an enabled non-Jewel IDE action sits between
  an outer and inner Jewel action in keymap iteration order, suppressing the outer Jewel action lets that
  non-Jewel action run ahead of the inner Jewel action; likewise, a custom subclass that overrides `update()`
  to disable itself despite an enabled Compose binding may still suppress an outer Jewel binding.
  It is enabled in modal contexts; `DumbAwareJewelActionBridgeAction` is the opt-in declaration
  variant for indexing-safe handlers, and runtime registrations always stay non-dumb-aware.
- The standard edit actions map onto `$Copy`/`$Cut`/`$Paste`/`$SelectAll`
  (`JewelActionMappings.installStandardMappings()`, installed by the bridge on panel composition,
  override-safe). `ComposeCopyProvider`/`ComposeCutProvider`/`ComposePasteProvider` check focused
  `Modifier.shortcut(JewelActions.Copy/Cut/Paste)` handlers first and fall back to focused Compose text
  semantics; for `JewelActions.SelectAll` (where platform `$SelectAll` has no provider interface), the
  bridge attaches a panel-scoped action following `$SelectAll`'s live active-keymap shortcuts so
  `Modifier.shortcut(JewelActions.SelectAll)` works on custom widgets while plain `TextField`s keep
  default `Ctrl`/`⌘`+`A` handling.
- Programmatic invocations route through `JewelBridgeActionInvoker` and `ActionManager.tryToExecute`
  against the focused descendant inside the active popup or panel wrapper (falling back to the wrapper
  itself), reporting update-time disablement as `ActionDispatchRejection.Disabled` and execution-time
  host rejections or ignored runs as `ActionDispatchRejection.RejectedByHost`; presentation sampling
  uses the same context component and rides the platform action-update cadence (a demand-gated
  `TimerListener`).

## Dispatch contract details

- Modifier-only key-downs (a lone <kbd>Ctrl</kbd> press) never participate in matching and never cancel
  a pending two-stroke sequence.
- Any consumed key-down arms one-shot suppression of the trailing KEY_TYPED event.
- An exact one-stroke command wins immediately over entering a two-stroke sequence sharing its first
  stroke. A pending sequence's nonmatching second stroke cancels the sequence and is consumed —
  deliberately stricter than the IntelliJ dispatcher, which consumes wrong second strokes and keeps
  waiting until a key release or timeout.
- Claims must be active when focus enters the component. Toggling a claim on between the strokes of an
  in-flight IDE chord leaves the platform's pending second-stroke state behind; it self-heals on key
  release or the `actionSystem.secondKeystrokeTimeout` timeout, but one unclaimed stroke can be
  swallowed in the interim.
- Claims are one-stroke sequences in this slice, and `Modifier.claimShortcut` enforces it: passing a
  two-stroke sequence throws `IllegalArgumentException`, because a claim resolves on a single key-down
  and a chord claim could never be invoked yet would shadow its first stroke. The IJPL bridge veto
  shares the engine's claim resolution (`ShortcutDispatchEngine.claimsStroke`), so the host never skips
  its own keymap for a stroke Jewel would not deliver. Two-stroke claims may follow in a later slice.
- `OnceUntilRelease` latches each held key stroke independently (so overlapping key holds such as `Ctrl+C`
  then `Ctrl+V` keep suppressing repeats of the first stroke after the second releases) for both claims
  and commands in both hosts, re-arms on release of either the latched key (including normalized numpad
  equivalents such as `NumPadEnter` for `Enter`) or any required modifier key, and resets on window focus
  loss (`LocalWindowInfo.isWindowFocused` -> `false`) so opening a modal dialog or switching apps never
  leaves a stale latch behind. In the IJPL bridge, `KEY_RELEASED` is observed via a non-consuming
  `IdeEventQueue` preprocessor as well as the wrapper's `KeyEventDispatcher`, so latches still clear even
  when a popup or another window intercepts the release; standalone windows rely on the window focus-loss
  reset whenever another window or dialog takes focus.
- Numpad keys sharing a virtual key code with a main-keyboard key (`NumPadEnter`, numpad arrows, Home/End,
  Page Up/Down, Insert, Delete) canonicalize to standard key location, and dedicated numpad codes
  (`VK_NUMPAD0..9`, `VK_ADD`, `VK_SUBTRACT`, etc.) canonicalize to numpad location, so Compose key-down/key-up
  matching, `JewelKeyStroke` equality, and Swing `KeyStroke` conversions agree across standalone and IDE hosts.
- When multiple different actions share the same shortcut, the innermost focused enabled binding wins in both
  hosts (standalone engine walks focused bindings inside-out with per-action `blocksOuterBindings`; IDE uses
  `ActionPromoter.suppress()` via `JewelActionBridgeAction` and the internal `JewelActionPromoter` extension,
  which also covers `ActionUtil.wrap(...)` wrappers and the panel-scoped `SelectAll` bridge action). Note that
  in the IDE `ActionPromoter.suppress()` compares binding depth only, so an inner chord prefix suppresses an
  outer exact one-stroke action sharing its first stroke.
- An armed two-stroke chord expires automatically after 2,000 ms (matching IJPL's `secondKeystrokeTimeout`,
  dropping silently so the next keystroke evaluates fresh), and any pointer press cancels a pending chord
  without consuming the pointer event.
- Chord, repeat-latch, and typed-suppression state resets when focus leaves a resolver root's subtree, when
  the hosting window loses focus, on explicit `reset()` or host disposal, or when a focused (or last remaining)
  root detaches, so returning focus never encounters stale state. Non-focusable popups (such as tooltips) leave
  the host root stack and pending engine state untouched.
- Claims follow root attach order (`attachedRoots.lastOrNull()`), so attaching a secondary root on the
  same host shadows outer roots' claims while attached; use a separate `JewelShortcutHostState` for an
  independent non-modal window.

The engine (`ShortcutDispatchEngine`) is free of Compose and AWT types; its unit tests in
`foundation/src/test/.../shortcut/` pin every rule above.

## Presentation and lifecycle events

Provide the host to the composition (`ProvideJewelShortcutHost(state) { … }`; the bridge does this
automatically inside `JewelComposePanel`) to observe presentation state and invocation events:

- A binding contributes dynamic presentation through `Modifier.shortcut(action, presentation = …)`
  (`ActionPresentationOverride`: text, description, visibility, `selected` for toggles, a
  host-interpreted icon slot, and `MenuDismissPolicy` — the four-state mirror of IJPL's
  `KeepPopupOnPerform`, carried as presentation state).
- `collectPresentationAsState(selector)` observes one projection gated by that projection's own
  equality, with demand-driven sampling (`ActionPresentationScheduler`) and explicit failure rows
  (`ActionResolution.Unregistered`, `NoFocusedBinding`).
- `JewelShortcutHostState.events` emits one `ActionInvocation` per completed Jewel-owned invocation, and
  `ShortcutRepeatPolicy` (`OnceUntilRelease` vs `RepeatWhileHeld`) controls auto-repeat delivery per
  binding or claim.

## Threading

The subsystem has a deliberate two-tier threading contract:

- **Dispatch is UI-thread-synchronous.** The handlers bound through `Modifier.shortcut`,
  `Modifier.claimShortcut`, and `Modifier.claimKeyEvent`, and the host entry points that invoke them
  (`onPreviewKeyEvent`, `claimsKeyDown`, `runResolvedInvocation`), run on the surface's UI thread —
  the AWT event dispatch thread in production — while a key event is being processed. Handlers must
  be fast and non-blocking: launch a coroutine for real work; a slow handler delays every subsequent
  keystroke. Violations are logged as errors; enable `JewelFlags.strictMode` (or the
  `jewel.strictMode` system property) to make them throw instead — keep strict mode on in
  development and tests.
- **Presentation sampling reads an atomically published snapshot and is eventually consistent.**
  `presentationFor` and the presentation scheduler read an atomically published snapshot of the
  focused bindings and may observe state up to one UI frame stale, which the next demand-driven
  sample corrects. That snapshot read is thread-safe, but whether sampling as a whole is safe from
  any thread depends on the backing: the IJPL bridge schedules platform `update()` asynchronously via
  IJPL's `Utils.expandActionGroupAsync` (honoring both `ActionUpdateThread.EDT` lock policies and
  `ActionUpdateThread.BGT` threading without ever running a nested EDT loop), publishing results back
  into the demand scheduler while returning the last published sample (or an initial disabled template
  sample) immediately; off-EDT `sampledPresentation` calls return only the cached published sample; with
  the standalone default backing, resolving a binding that computes its presentation from the focused
  Compose tree must run on the surface's UI thread. Presentation is advisory: dispatch itself never
  consults it.

`InMemoryJewelKeymap` and `DefaultJewelActionRegistry` are safe to read from any thread; perform
writes (binding, rebinding, action registration) on the UI thread.

## Testing your shortcuts

Shortcuts are verifiable in plain Compose UI tests — no window, no AWT hooks, no IDE fixture:

- The resolver root participates in the scene's key-event **preview pass**, so idiomatic scene
  injection drives real dispatch (focus resolution, fall-through, claims, chords):

  ```kotlin
  composeRule.setContent { Box(state.resolverRootModifier) { AppContent() } }
  composeRule.onNodeWithTag("editor").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.S) } }
  ```

  In production this scene-level lane coexists safely with the AWT hooks: events they consume never
  reach the scene, and events they passed re-evaluate to the same pass without mutating dispatch
  state. KEY_TYPED suppression remains the AWT hooks' job — test scenes never produce typed events,
  so assert typed suppression at the engine level or in the live harnesses.

- Bindings and claims publish **semantics**, so tests (and tooling) can discover keyboard behavior:
  `JewelShortcutActions` carries the bound action IDs, `JewelClaimedShortcuts` the claimed sequences'
  display texts:

  ```kotlin
  composeRule.onNodeWithTag("editor")
      .assert(SemanticsMatcher.expectValue(JewelClaimedShortcuts, listOf("Ctrl+Enter")))
  ```

`ShortcutUiTest` in the foundation test sources demonstrates both, and `ShortcutTesterUiTest` in the
showcase module shows the same pattern against real application UI.

## Try it live

The standalone sample is the living demo: it installs one host at the window root (see
`ShowcaseShortcutHost` and `Main.kt`), binds ambient page-navigation commands
(<kbd>Alt</kbd>+<kbd>W</kbd>/<kbd>C</kbd>/<kbd>M</kbd>/<kbd>S</kbd>), switches component sections with
<kbd>⌘</kbd>/<kbd>Ctrl</kbd>+<kbd>↑</kbd>/<kbd>↓</kbd>, and ships a **Shortcuts** page
(<kbd>Alt</kbd>+<kbd>S</kbd>) — an interactive tester covering nested overrides, claims vetoing
commands, typed suppression, and two-stroke chords.

## Status

Implemented and tested: dispatch core (repeat policies and menu scopes), standalone resolver, in-memory
keymap model, presentation model and demand-driven scheduler, action lifecycle events, popup/menu shortcut
propagation, the bridge claim lane, and the bridge action registry/invoker (attach-or-register, standard
edit mappings, platform-routed invocation, platform-cadence presentation updates) with platform-level
integration tests. Action-bound UI components and live keymap settings follow in separate layers.
