# Component API, Lists, And Performance

Use this reference when UI work touches reusable composable APIs, modifier handling, lazy lists, recomposition behavior,
object churn, animations, or performance-sensitive composition.

## Modifier And Component API Contract

For reusable UI:

```kotlin
@Composable
fun ExampleCard(
    model: ExampleCardModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) { ... }
```

Rules:

- Required data and callbacks first.
- `modifier: Modifier = Modifier` is the first optional parameter.
- Slot lambdas last; primary slot is named `content` when present.
- Forward `modifier` to the outermost layout-producing call.
- Chain internal modifiers after caller `modifier`.
- Do not expose `MutableState<T>` in reusable component APIs. Prefer value + `onXxxChange` or a dedicated state holder.
- Do not pass presenters/coordinators into leaf components. Pass view models and callbacks.

## Lazy Lists

- Use stable, unique keys for items that can move, be inserted, be removed, or stream in.
- Use `contentType` for heterogeneous rows so Compose can reuse compatible item compositions.
- Never use list index as a key for reorderable/dynamic data.
- Verify key uniqueness when rows merge data from multiple sources.

Common examples that should be key/content-type aware include timeline rows, message/log rows, sidebar rows,
settings/provider rows, permission cards, and generated lists of tools, plans, or knowledge items.

## Performance Smells To Check

Flag and fix when touched:

- file/DB/network/process/config reads in composition
- JSON parsing in composition
- Move `filter`, `map`, `groupBy`, and `sortedBy` collection chains out of hot composable bodies.
- Markdown parsing, permission payload parsing, model filtering, or command/menu construction runs in composition when
  it can be projected by a presenter/coordinator.
- Avoid rebuilding `listOf(...)`, `mapOf(...)`, fresh UI models, or anonymous objects on every composition and passing
  them to child composables.
- Replace broad app or session state reads in composables with presenter-projected view models.
- Combine multiple flows in the presenter instead of collecting them independently when their values must stay coherent
  on screen.
- `mutableStateOf<Int|Long|Float|Double>` instead of typed primitive factories
- `Animatable(...)` created without `remember` or hoisting
- Run target-driven animations inside `LaunchedEffect(target)` instead of launching them from the composition body or an
  event scope.
- Infinite animations are hosted in surfaces that remain composed while hidden or offscreen.
- animated values passed to `Modifier.offset(x = ...)`, `alpha`, `rotate`, or `scale` when `offset { ... }` or
  `graphicsLayer { ... }` can defer per-frame reads to layout/draw
- `Modifier.composed { ... }` for new custom modifiers when `Modifier.Node` or a plain modifier factory is enough

## Healthy Patterns To Preserve

Do not refactor these just because they use Compose state/effects:

- Keep `remember { MutableInteractionSource() }` and derived hover or press state inside the composable for visual
  feedback.
- Keep `FocusRequester` paired with `LaunchedEffect(Unit) { requestFocus() }` when focusing a newly shown editor.
- Local text-field/editing state that is committed through callbacks.
- Keep UI-only expanded or collapsed toggle state in the composable when it does not mirror persisted settings.
- Demo/debug entries that build sample state locally, as long as production code keeps protocol/runtime work behind the
  real owning seam.
- Composition executing a scroll/focus/dialog command returned by a state holder or presenter event.

If a pattern is healthy, say so briefly and move on; do not manufacture a refactor.
