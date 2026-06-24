# Effects And Remember Rules

Use this reference when UI work touches `remember`, `derivedStateOf`, `LaunchedEffect`, `DisposableEffect`,
`snapshotFlow`, `produceState`, coroutine scopes, animation triggers, or external listener bridges.

## `remember` Rules

- `remember` is not an initializer for changing props.
- If a remembered calculation depends on inputs, pass those inputs as keys.
- Do not write `remember { mutableStateOf(prop) }` unless the prop is deliberately only an initial value.
- Cheap derived values usually do not need `remember`.
- Use `derivedStateOf` only when observable inputs change more often than the derived output.
- Do not launch work from `remember { ... }`.

## Effects Decision Table

| Need | API |
|---|---|
| Run suspending work when keys change | `LaunchedEffect(key)` |
| Register external listener and clean it up | `DisposableEffect(key)` |
| Publish latest composition value to an imperative API after composition | `SideEffect` |
| Convert a callback/suspend source into Compose `State` | `produceState` |
| Observe Compose state as a Flow inside a coroutine | `snapshotFlow` inside `LaunchedEffect` |
| Launch coroutine from click/gesture event | `rememberCoroutineScope()` from the event handler |
| Keep latest callback in a long-lived effect without restarting | `rememberUpdatedState` |

Rules:

- `LaunchedEffect(Unit)` is fine for genuine one-shot-on-enter work. Flag it only when it captures changing values
  without `rememberUpdatedState` or an appropriate key.
- `rememberCoroutineScope()` is for event-driven work, not composition startup or target-driven animations.
- Effects should call presenter APIs or state-holder methods; they should not contain business workflows.
- Every `DisposableEffect` needs meaningful cleanup.
