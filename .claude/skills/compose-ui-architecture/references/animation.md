# Animation

Use this reference when UI work touches `animate*AsState`, `updateTransition`, `Animatable`, `AnimatedVisibility`,
`AnimatedContent`, `SharedTransitionLayout`, shared elements, `animateContentSize`, `rememberInfiniteTransition`,
gesture-driven motion, or lazy-list item animation.

Animation bugs are usually state, effect, or performance bugs in disguise: an animation object is not remembered,
target-driven work is launched from the wrong place, or per-frame values are read during composition.

## Choose The Smallest API

| Need                                                           | Prefer                                                                   |
|----------------------------------------------------------------|--------------------------------------------------------------------------|
| One value follows state, such as alpha, color, offset, or size | `animate*AsState`                                                        |
| Several values move together from the same state change        | `updateTransition`                                                       |
| Gesture, fling, snap, cancellation, or a multi-step sequence   | `remember { Animatable(...) }`                                           |
| Enter/exit for content in the tree                             | `AnimatedVisibility`                                                     |
| Swap between distinct content states                           | `AnimatedContent` or `Crossfade`                                         |
| Visually connect matching content across states/screens        | `SharedTransitionLayout` plus `Modifier.sharedElement()` or `sharedBounds()` |
| Animate a size change of the same content                      | `Modifier.animateContentSize()`                                          |
| Insert/remove/reorder items in a lazy list                     | `Modifier.animateItem()` with stable `key =`                             |
| Decorative looping motion                                      | `rememberInfiniteTransition`, only while the node should visibly animate |

Do not default to `Animatable` plus `LaunchedEffect` for a simple state target. If a single value follows a target
state, `animate*AsState` is shorter, cancels and retargets correctly, and avoids an extra coroutine surface.

## Remember Stateful Animation Objects

`Animatable(...)` and `MutableTransitionState(...)` are stateful objects. Creating them directly in a composable body
recreates them on recomposition and can reset velocity or progress.

```kotlin
// Wrong — recreated on recomposition
val alpha = Animatable(1f)

// Right
val alpha = remember { Animatable(1f) }
```

If the parent deliberately re-keys the subtree, the remembered animation resets with that key. That is usually
preferable to silently carrying animation state across different row or screen identities.

## Launch Target-Driven Animations From Effects

When imperative animation is genuinely needed, state-targeted animation belongs in a `LaunchedEffect(target)`. Do not
call `rememberCoroutineScope().launch { ... }` from the composition body.

```kotlin
// Wrong — launches during composition and can run on every recomposition
val scope = rememberCoroutineScope()
val offset = remember { Animatable(0f) }
scope.launch { offset.animateTo(target) }

// Right — cancels and restarts when the target changes
val offset = remember { Animatable(0f) }
LaunchedEffect(target) {
  offset.animateTo(target, animationSpec = spring())
}
```

`rememberCoroutineScope()` is appropriate inside click, drag, and gesture callbacks where the animation is event-driven
rather than target-driven.

## Keep Related Values On One Clock

If alpha, scale, color, or position all move from the same state change, use one `updateTransition(targetState)` instead
of a pile of independent `animate*AsState` calls. A single transition retargets coherently and gives each child
animation a label.

```kotlin
val transition = updateTransition(targetState = expanded, label = "expandCard")
val alpha by transition.animateFloat(label = "alpha") { if (it) 1f else 0.6f }
val scale by transition.animateFloat(label = "scale") { if (it) 1f else 0.95f }
```

For one animated property, keep `animate*AsState`; do not introduce a transition just for ceremony.

## Prefer Interruptible Motion

For user-interactive state changes, prefer `spring()` or the Compose default unless product/design explicitly requires a
fixed duration. `tween(...)` and `keyframes { ... }` are useful for choreographed or brand-timed motion, but they are
easier to make feel abrupt when the target changes mid-animation.

Always give shared/tooling-visible animations a meaningful `label`.

## Defer Per-Frame Reads

Animated values change every frame. Reading them in composition recomposes the affected composable every frame. Prefer
lambda modifiers that read during layout or draw.

```kotlin
val x by animateFloatAsState(targetValue = targetX, label = "panelX")

// Wrong — composition-phase read
Box(Modifier.offset(x = x.dp))

// Right — layout-phase read
Box(Modifier.offset { IntOffset(x.roundToInt(), 0) })
```

Common fixes:

- Read alpha, scale, rotation, and translation in `Modifier.graphicsLayer { ... }`.
- Read offset or position in `Modifier.offset { ... }` or a custom `layout { ... }`.
- Move purely visual background or tint changes to draw-phase modifiers such as `drawBehind { ... }`.
- Use `Modifier.animateItem()` on the item root for list item movement instead of hand-rolled offsets.

Measure before and after if the change is performance-motivated; logging with `SideEffect` is enough to confirm whether
the composable still recomposes on each animation frame.

## Use AnimatedContent Deliberately

`AnimatedContent` is for switching between distinct content. If `targetState` is a broad screen model, provide
`contentKey` for the field that actually defines different content; otherwise unrelated state changes can retrigger
transitions.

```kotlin
AnimatedContent(
  targetState = screenState,
  contentKey = { it.mode },
  transitionSpec = { fadeIn() togetherWith fadeOut() },
  label = "modeSwap",
) { state ->
  // render mode
}
```

Use `Crossfade` for a plain fade. Use `animateContentSize()` when the same content expands or collapses.

## Shared Element Transitions

Use shared element transitions to visually connect content that appears in two UI states, such as a row expanding into a
detail pane. They are best paired with `AnimatedContent`, `AnimatedVisibility`, or the project
navigation/screen-switching container that provides an `AnimatedVisibilityScope`.

Core pieces:

- Wrap the common ancestor in `SharedTransitionLayout`; it provides the `SharedTransitionScope`.
- Apply `Modifier.sharedElement(...)` when the content is semantically the same in both places, such as the same icon,
  avatar, or title.
- Apply `Modifier.sharedBounds(...)` when the content differs but should transform through the same container bounds,
  such as a compact row becoming a richer detail card.
- Create the match with `rememberSharedContentState(key = ...)`; use stable, unique keys derived from item identity and
  element role, not fragile reused strings.
- Pass the needed `SharedTransitionScope` and `AnimatedVisibilityScope` explicitly when the hierarchy is shallow. Avoid
  broad `CompositionLocal` scope plumbing unless it genuinely reduces deep nesting complexity.

Prefer `sharedBounds()` for text whose font weight, style, color, or shape changes during the transition;
`sharedElement()` expects visually consistent content. For unsupported shape/content-scale cases, use shared bounds plus
explicit enter/exit or `animateEnterExit()` behavior rather than pretending the shared element API will interpolate
everything automatically.

Modifier order matters around shared elements. Modifiers before `sharedElement()` or `sharedBounds()` define the matched
constraints and bounds, while modifiers after them are measured inside the animated constraints.

Keep modifier order consistent between source and target elements, and place modifiers excluded from shared bounds
before the shared modifier. Inconsistent padding or size order between source and target causes layout jumps.

When manually controlling shared-element visibility, remember that a matched element can remain in the tree while
invisible and still react to size/position changes. Remove it once the transition is finished, using the
transition-active state exposed by the shared transition scope when available.

Desktop caveats:

- Verify the APIs exist in the Compose Multiplatform/Foundation version the project uses before committing to the
  pattern.
- Do not use shared transitions to bridge native Swing/AWT/View interop surfaces; keep native interop behind seams and
  animate Compose-owned content around it.
- Treat shared transitions as UI state/animation only. Selection, navigation, persistence, and workflow decisions still
  belong in the presenter/coordinator layer.

## Keep Infinite Animation Scoped

`rememberInfiniteTransition` runs as long as the node is composed. Do not keep decorative shimmer, pulse, or spinner
work in a surface that remains composed while hidden or offscreen. Gate it behind visibility/loading state, and expose
an explicit app-level reduced-motion seam for decorative motion when the project supports that preference.

## Gesture-Driven Motion

For drag/follow/fling behavior, `Animatable` earns its keep:

- `snapTo(...)` while following pointer movement
- `animateDecay(...)` for fling after release
- `animateTo(...)` for snap-back or settling
- launch from the gesture callback scope, not from composition

Do not use `animate*AsState` for continuous pointer tracking.

## Anti-Patterns Checklist

- `Animatable(...)` or `MutableTransitionState(...)` created without `remember`.
- Do not pair `Animatable` with `LaunchedEffect(target) { animateTo(target) }` when `animate*AsState` suffices.
- `rememberCoroutineScope().launch { animateTo(...) }` in the composition body.
- Separate `animate*AsState` calls for values that should share one transition clock.
- Missing animation `label` on reusable or tooling-visible UI.
- Animated values are read by `Modifier.offset(x = ...)`, `alpha`, `rotate`, `scale`, or `padding(...)`
  during composition.
- Do not pass a broad screen model to `AnimatedContent` without specifying a `contentKey`.
- Do not use shared-element keys that are duplicated, built from untyped strings, or missing the item identity and
  element role.
- `sharedElement()` used for visually different content where `sharedBounds()` would fit better.
- Inconsistent modifier order around `sharedElement()` / `sharedBounds()` between source and target.
- Do not use shared transitions to hide presenter, navigation, or workflow state-ownership bugs.
- `rememberInfiniteTransition` kept alive for hidden/offscreen decorative UI.
- Do not implement manual lazy-list item movement when `Modifier.animateItem()` with stable keys handles the transition.

## Sources

- https://developer.android.com/develop/ui/compose/animation/choose-api
- https://developer.android.com/develop/ui/compose/animation/value-based
- https://developer.android.com/develop/ui/compose/animation/customize
- https://developer.android.com/develop/ui/compose/animation/composables-modifiers
- https://developer.android.com/develop/ui/compose/animation/shared-elements
- https://developer.android.com/develop/ui/compose/performance/bestpractices#defer-reads
- https://developer.android.com/develop/ui/compose/lists#item-animations
