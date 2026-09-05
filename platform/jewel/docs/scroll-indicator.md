# Scroll indicator

`Modifier.scrollIndicator()` draws a Jewel-styled scrollbar over the content of the layout node it is applied to. It is
an opt-in alternative to `VerticalScrollbar` and `VerticallyScrollableContainer`, not a replacement for them.

> [!WARNING]
> This API is experimental (`@ApiStatus.Experimental`) and may change without notice.

## When to use it

The scrollable containers lay the scrollbar out as a sibling of the content. `Modifier.scrollIndicator()` decorates the
content in place instead, so you can add a scrollbar without wrapping anything in another container. It reserves layout
space on exactly the same terms the containers do: on macOS with `AlwaysVisible`, where the platform scrollbar is not an
overlay, the indicator gets its own lane and the content is measured narrower. Everywhere else it floats over the
content and the layout is untouched.

Prefer `VerticallyScrollableContainer` for ordinary UI. Reach for the modifier when:

* you need the indicator to overlay content whose measurement must not change;
* you already own the scrolling and the layout, and only want the Jewel-styled bar painted on top;
* wrapping the content in another container would disturb the layout (e.g., inside an existing `Layout`).

## Basic usage

The modifier takes the same `ScrollableState` your scrollable content uses, and defaults to a vertical indicator:

```kotlin
val scrollState = rememberLazyListState()

LazyColumn(state = scrollState, modifier = Modifier.fillMaxSize().scrollIndicator(scrollState)) {
    items(items) { Text(it) }
}
```

For non-lazy content, apply it alongside the scroll modifier. Put `scrollIndicator()` **before** `verticalScroll()`, so
the indicator is not scrolled along with the content:

```kotlin
val scrollState = rememberScrollState()

Column(Modifier.fillMaxSize().scrollIndicator(scrollState).verticalScroll(scrollState)) {
    for (line in lines) {
        Text(line)
    }
}
```

For a horizontal indicator, pass the orientation explicitly:

```kotlin
val scrollState = rememberScrollState()

Row(Modifier.fillMaxWidth().scrollIndicator(scrollState, Orientation.Horizontal).horizontalScroll(scrollState)) {
    for (item in items) {
        Text(item)
    }
}
```

## Supported scroll states

The indicator reads its metrics from
[`ScrollableState.scrollIndicatorState`](https://developer.android.com/reference/kotlin/androidx/compose/foundation/ScrollIndicatorState),
which Compose Foundation implements for `ScrollState`, `LazyListState`, `LazyGridState`, `LazyStaggeredGridState`, and
`PagerState`. For lazy layouts, those metrics are estimated by Compose Foundation itself, so the thumb can move slightly
non-linearly when item sizes vary a lot.

If a `ScrollableState` does not provide indicator metrics, nothing is drawn and no pointer events are consumed. On
macOS an always-visible style still reserves its lane, so the layout is unchanged whether or not the metrics arrive.

## Parameters

| Parameter | Description |
|---|---|
| `scrollState` | The `ScrollableState` the indicator reflects and controls. |
| `orientation` | The scroll axis. Defaults to `Orientation.Vertical`. |
| `style` | The `ScrollbarStyle` to use. Defaults to `JewelTheme.scrollbarStyle`. |
| `reverseLayout` | Mirrors the thumb, for content laid out in reverse. Defaults to `false`. |
| `enabled` | When `false`, the indicator is still drawn but is fully inert: it does not expand on hover, take clicks or drags, or forward the wheel over a reserved lane. Defaults to `true`. |
| `keepVisible` | Keeps the indicator visible when it would otherwise fade out. Defaults to `false`. |

## Behaviour

The indicator uses the same colours and metrics as `VerticalScrollbar`. Its interactions match the Swing scrollbar:

* the track waits `expandDelay`, then expands over `expandAnimationDuration`, and waits `collapseDelay` before it collapses. The `WhenScrolling` styles default these waits to 150 ms and 300 ms, matching the Swing painter;
* thumb and track colours animate between their resting, active, and hovered variants, and the track background is
  painted only while expanded;
* dragging the thumb scrolls the content, positioning it absolutely against the pointer as the Swing scrollbar does, so
  overshooting an end and coming back cannot drift;
* it is placed at the end of the layout: a vertical indicator on the right edge in LTR and the left edge in RTL, a
  horizontal one along the bottom edge.

### Visibility

The indicator is revealed by scrolling, by dragging it, or by hovering its track. It then hides again once
`ScrollbarVisibility.lingerDuration` elapses.

Pointer movement acts as a **keep-visible latch**: while the indicator is already visible, moving the pointer anywhere
over the content re-arms the timeout, so it stays on screen as long as the user keeps moving. This matches
`VerticallyScrollableContainer` and the Swing viewports — and, as there, a move over *hidden* content does not reveal
it. Setting `keepVisible = true` applies the same prolonging effect unconditionally.

`ScrollbarVisibility` selects between the two platform behaviours:

| Visibility | Behaviour |
|---|---|
| `WhenScrolling` | The indicator overlays the content and fades out after `lingerDuration`. On macOS this is what the system preference asks for by default. |
| `AlwaysVisible` | The indicator is always shown and uses the opaque colour set. It is the default on Windows and Linux, and on macOS when the system preference asks to always show scrollbars. Only on macOS does it also reserve a lane beside the content: Windows and Linux always overlay, as `ScrollableContainer` does. |

### Track clicks

Pressing the track scrolls according to `ScrollbarStyle.trackClickBehavior`:

| Behaviour | Effect |
|---|---|
| `JumpToSpot` | Jumps so the thumb centres on the pressed spot, and keeps following the pointer while it is held. On macOS this follows the system preference. |
| `NextPage` | Scrolls by one viewport towards the press, then repeats while held. The direction is latched at press time, so the thumb stops when it reaches the pointer instead of paging back and forth. This is the default on Windows and Linux, matching the Swing scrollbar, which reserves the jump for a middle click. |

Both scroll instantly rather than animating, matching `VerticalScrollbar`: an animation would still be running when the
next paging step is due.

The indicator adds no semantics of its own, so it neither exposes nor hides anything for assistive technologies: the
scrollable content it decorates keeps its own semantics and scroll actions.

While the indicator is hidden, it does not consume pointer events, so clicks near the edge of the content still reach
the content underneath. A visible overlay track also lets clicks through until it is opaque or expanded. Hover the
track to expand it, then click.

## Limitations

* **Content padding is yours to apply.** While the indicator floats, it overlays a strip of your content.
  [`scrollbarContentSafePadding()`](../ui/src/main/kotlin/org/jetbrains/jewel/ui/component/ScrollableContainer.kt) tells
  you how wide that strip is — it is zero exactly when the scrollbar does not overlay anything. Apply it yourself to the
  elements that must stay clear of the scrollbar; it is deliberately not applied for you, because only you know which
  parts of your content are interactive (text, buttons) and which are decoration that may sit underneath (dividers,
  backgrounds).

## Relationship to Compose Foundation

Compose Foundation ships the `ScrollIndicatorState` contract as stable API, and this modifier consumes it directly.
It deliberately does **not** reimplement the withdrawn `Modifier.scrollIndicator(factory, state, orientation)` factory
API: Jewel owns the rendering, so no factory indirection is needed.

## See also

* [`Scrollbars.kt`](../samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/components/Scrollbars.kt) —
  the showcase, which switches between the container and modifier approaches
* [Scrollbar guidelines](https://plugins.jetbrains.com/docs/intellij/scrollbar.html) on the IJP SDK webhelp
