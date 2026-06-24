# Composable API Hygiene And Static-Rule Signals

Use this reference when an audit includes reusable composable APIs, large UI files, Detekt findings, or lint-like
Compose smells. These are review heuristics, not automatic findings: read the surrounding code and report only when the
issue affects caller control, state ownership, recomposition, readability, or testability.

## Sources distilled

- Detekt comments, naming, complexity, coroutines, performance, potential-bugs, exceptions, and style rules:
  https://detekt.dev/docs/rules/comments and sibling rule pages.
- Nacho López' Compose Rules: https://mrmans0n.github.io/compose-rules/latest/rules/.
- AndroidX Compose API/component guidelines, adapted for Compose Desktop/Jewel.

## What to flag during a desktop Compose UI audit

### Public API documentation and naming

- Public or cross-file reusable composables should have KDoc when their purpose, state contract, slots, modifier
  expectations, side effects, or desktop interop behavior are not obvious from the signature.
- KDoc must describe the current signature. Stale `@param`/`@property` tags are worse than no docs because callers learn
  the wrong contract.
- KDoc on private functions/properties is a smell when it compensates for vague naming, too much scope, or a function
  that should be split. Prefer a better name, smaller function, or extracted section over explanatory comments.
- Unit-returning content composables are UI entities and should be PascalCase. Value-returning composables and
  `remember...` helpers should be camelCase.
- Boolean state should read like a predicate (`is...`, `has...`, `can...`) when it appears in UI APIs or row models.

### State and dependency ownership

- Do not pass `MutableState<T>` into reusable components. Pass values down and events up (`value`, `onValueChange`) or
  use a dedicated state holder with a domain-specific API. Treat `State<T>` parameters as a lead to verify rather than an
  automatic finding: read-only state can be acceptable when the ownership and lifecycle contract is explicit.
- Do not pass inherently mutable data (`MutableList`, `ArrayList`, mutable maps/sets, mutable model objects) into
  composables unless it is wrapped by observable state and the ownership model is explicit.
- Treat `List`, `Set`, and `Map` parameters in hot or frequently recomposed composables as a stability lead, not an
  automatic bug. Check whether the project has a Compose stability config, immutable collections, or stable wrapper
  types before reporting. Prefer `ImmutableList`/persistent collections or stable screen-row models when churn matters.
- Dependencies should be explicit at screen boundaries. Avoid reading service locators, app stores, presenters,
  provider clients, or desktop APIs from leaf components. Pass the state and callbacks that the leaf needs.

### Composable function contracts

- A composable should either emit UI or return a value, not both. If it needs to report data, expose callbacks or a
  state holder instead of returning values while also emitting content.
- A reusable content composable should emit a cohesive root node. Multiple sibling emitters are only acceptable when
  the function is explicitly scoped to a parent layout (`ColumnScope`, etc.) and the API makes that coupling obvious.
- Avoid marking pure helpers as `@Composable` when they do not read composition data, snapshot state, or emit content.
- `@ReadOnlyComposable` is only for functions that read composition data and do not emit UI, call effects, or mutate
  composition. Do not use it on content emitters.
- `@Preview`-only helpers should stay private when previews exist in the target source set.

### Parameter order, slots, and callbacks

- Required non-slot parameters come first; trailing content slots stay last so call sites keep the normal Compose
  trailing-lambda shape.
- `modifier: Modifier = Modifier` is the first optional parameter for reusable content composables.
- The main modifier parameter is named exactly `modifier`. Subcomponent modifiers use names like `headerModifier`.
- Content slots should be trailing lambdas; event callbacks should not be the trailing lambda because call sites then
  look like content slots.
- Event callbacks should be named as current-tense events (`onClick`, `onChange`, `onSubmit`) rather than past-tense
  notifications (`onClicked`, `onChanged`) unless project conventions intentionally differ.
- If a content slot is invoked from multiple branches, check whether the slot lifecycle is preserved. Consider
  `movableContentOf` only with an explicit freshness strategy: create `val latestContent = rememberUpdatedState(content)`
  outside the unkeyed remembered movable lambda, then read `latestContent.value()` inside it. Alternatively key/recreate
  movable content when resetting slot state is intentional, or prefer a layout API that preserves slot lifecycle without
  capturing a stale slot lambda.

### Modifier contract

- Reusable content composables should accept a `modifier` unless there is a concrete reason callers must not control the
  outer layout.
- Apply the passed `modifier` once, to the outermost emitted layout, and chain internal defaults after it.
- Do not reuse the caller's `modifier` on child elements or multiple siblings; create fresh `Modifier` chains for
  internal children.
- Keep modifier order meaningful: external layout/size/padding before draw/clip/background before pointer input when
  that order affects hit area, ripple shape, or visual bounds. Verify with `references/modifiers/modifier-order.md` when
  unsure.
- Prefer plain `Modifier` extension functions or `Modifier.Node` for custom modifiers. Treat `Modifier.composed {}` as a
  lead for composition-phase state reads or unnecessary allocation; it is acceptable only when composition-time locals or
  theme values are genuinely needed.

### Effects and coroutine/cancellation signals

- `LaunchedEffect`, `DisposableEffect`, `produceState`, and `snapshotFlow` keys must match the values whose change should
  restart work. Use `rememberUpdatedState` for changing callbacks that should not restart long-lived work.
- Do not eagerly read a delegated `rememberUpdatedState` value inside `remember {}`; it captures the initial value.
  Either defer the read until invocation or key `remember` on the source value.
- No `GlobalScope`, uncancelled ad-hoc `CoroutineScope`, or `Thread.sleep` in UI/effect code. Route long-lived work to
  presenter/coordinator scopes or composition-managed effects with cleanup.
- For UI callback paths that start durable or business work, always report the destination layer in the fix: the UI should
  call a presenter/coordinator event or a caller-owned suspending callback, not own repository writes directly.
- In suspending code reached from UI, `runCatching` and broad catches must not swallow `CancellationException`; rethrow
  cancellation immediately before converting other failures to UI state.
- Convert non-cancellation failures into presenter/state-holder state that the UI renders (`errorMessage`, banner state,
  retry affordance), instead of ad-hoc `showError` side effects from leaf components.
- Functions returning `Flow` should normally not be `suspend`; invoking them should be side effect free until collection.

### Detekt shape signals in UI files

- Long composables, deeply nested emitter trees, complex conditions, nested scope functions, large files/classes, and too
  many helpers usually indicate missing extraction boundaries. Recommend concrete sections to extract: toolbar, row,
  popup, footer, empty state, or state-holder/presenter logic.
- Do not treat Composable verbosity as a licence to hide business logic in UI. Branching that chooses UI sections is fine;
  parsing payloads, permission decisions, provider routing, and persistence belong outside composition.
- Repeated strings/numbers in UI are findings only when they encode user-visible copy, test tags, protocol keys,
  dimensions, durations, or policy. Prefer named constants/theme tokens; do not churn harmless `0`, `1`, or `2`.
- Performance rules such as spread operators, chained collection transforms, range `forEach`, primitive boxing, or regex
  creation matter most in composition, lazy rows, animation, scroll, or frequently recomposed presenter projection paths.
  Outside hot paths, report them as low-priority cleanup or skip them.

## Reporting guidance

- Group these under `Modifier/API shape`, `Boundary and state ownership`, `Effects and coroutine usage`, or
  `Lazy lists and recomposition` rather than creating a wall of lint findings.
- Prefer one systemic finding with representative examples over many one-line style nits.
- If Detekt would catch the issue mechanically, say so, but still explain the Compose runtime or API-design consequence.
- Do not import Android-only advice: no Android lifecycle owners, Navigation, Material 2/3 migration, Accompanist,
  baseline profiles, or R8 for Desktop/Jewel audits.
