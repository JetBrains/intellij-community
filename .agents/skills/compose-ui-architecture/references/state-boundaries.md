# State Boundaries

Use this reference when UI work touches state ownership, presenter/coordinator boundaries, desktop lifecycle, flow
collection, `CompositionLocal`s, or where logic should be tested.

## Presenter vs State Holder vs Composition

Use the project rule from the relevant architecture docs:

| Logic | Where it belongs |
|---|---|
| Business decisions, RPC, persistence, settings, workspace, model/provider state | Presenter/coordinator |
| Screen projections and derived render models | Presenter/coordinator |
| UI-adjacent multi-step state machines needing layout signals | `@Stable` state holder |
| Thin effect bridges, focus, animation, hover, expansion | Composition |

State holders should usually take values, not Compose UI objects. Prefer:

```kotlin
val intent = controller.onLayoutChanged(totalItemCount = count, isAtBottom = atBottom)
when (intent) { is ScrollIntent.Snap -> listState.scrollToItem(intent.index) }
```

Avoid stuffing `LazyListState`, presenters, or long-lived scopes into state holders unless the tradeoff is deliberate
and tested.

## Desktop Architecture Patterns

Desktop Compose apps often use manual dependency wiring, long-lived tool-window or session state, Swing/AWT seams, and
runtime/service boundaries. That changes the default architecture instincts:

- **The application shell owns the process and session lifetime.** Application entry points and session wiring create
  and shut down presenters, select the active session, and manage runtime profiles. Reusable composables must not look
  up or instantiate these dependencies.
- **Presenter projections beat ad hoc collection.** A screen should prefer one coherent projected view model flow over
  several independently collected raw flows. This avoids stale pairings such as body state from one emission and
  workspace/model status from another.
- **Desktop lifecycle is explicit.** There is no Android `LifecycleOwner` safety net unless the project explicitly adds
  one. Start file watchers, subprocesses, autosync, and persistence from presenter/runtime objects with explicit
  close/cancel paths; compositions only subscribe and render.
- **Swing/AWT is an interop boundary.** File choosers, popups, clipboard/action-system hooks, and host components belong
  behind a small callback or interop helper. Use `jewel-swing-interop` for the mechanics; do not leak AWT details into
  leaf composables.
- **Window, focus, and shortcuts are UI wiring.** The composition may request focus, execute scroll/focus commands,
  and bind keyboard shortcuts, but the decision that a shortcut means "abort", "archive", or "send" belongs in the
  presenter/app-state layer.
- **Density/theme are environment, not business state.** Theme and density locals are fine; model/provider/session state
  hidden in locals is not.
- **Long-running desktop work must be cancellable.** Anything tied to a session or app lifetime needs an owner with a
  close path. Avoid `GlobalScope`, unmanaged `Thread`, and composition-launched loops that outlive the surface.

## Desktop Flow Collection

Android guidance often says `collectAsStateWithLifecycle()`. Desktop Compose code usually does not have that lifecycle,
so do not cargo-cult it or add Android lifecycle dependencies unless the project already depends on them.

Use these rules instead:

- Collect presenter-owned `StateFlow`s at screen/root boundaries, not deep in leaf components.
- Prefer a single screen projection flow, such as a `*ScreenState`, when multiple raw values must stay coherent.
- Keep collection read-only in UI; events go back through callbacks/presenter APIs.
- If collection would start expensive upstream work, that upstream should be owned by a presenter/coordinator and tied
  to session/app lifetime, not to whether a leaf composable happens to be visible.
- One-shot UI events should stay explicit: presenter emits an event/command, composition executes the UI-only action
  such as scroll, focus, dialog display, or clipboard interaction.

If two `collectAsState()` calls must stay synchronized for the screen to render valid state, combine them into a single
presenter-projected view model.

## CompositionLocal Guardrails

Composition locals are for tree-scoped environment values with sensible defaults. Theme, density, and host-component
locals can be intentional. Business state and action routing usually should not be hidden in locals.

Avoid locals for actions or app state:

- no `LocalPresenter`
- no `LocalSessionModel`
- no `LocalOnClick`
- no `LocalShowSnackbar`
- no hidden RPC/settings/workspace accessors

Prefer explicit parameters or presenter-projected screen state.

## Testing Implications

Architecture fixes should move logic into places that are cheap to test:

- Test presenter and coordinator decisions with plain unit tests, or use end-to-end tests when verifying RPC and event
  wiring.
- State holders: plain unit tests with method calls and command/intention assertions.
- Composition wiring: Compose UI tests only for visibility, callbacks, focus/scroll execution, and semantic behavior.

If a bug needs a full Compose UI test just to exercise business logic, the logic is probably still too low in the tree.
