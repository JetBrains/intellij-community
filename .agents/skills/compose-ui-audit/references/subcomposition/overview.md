# Compose Subcomposition — Power vs Cost

**Core principle:** Avoid subcomposition unless measurement genuinely drives composition. If
the same layout can be expressed with standard modifiers or pre-calculated derived state, prefer
that.

> **Routing:** Use this workflow when profiling shows unexpected composition overhead during
> resize, scroll, or layout, or when reviewing whether `BoxWithConstraints` can be replaced.

## Workflow

### Phase 1 — Measure

- [ ] **1. Profile during resize.** Resize the window. If CPU spikes, capture a sample:

```bash
# JFR — pass JVM flags via JAVA_TOOL_OPTIONS, not --args
JAVA_TOOL_OPTIONS="-XX:+FlightRecorder -XX:StartFlightRecording=duration=30s,filename=resize.jfr" \
  ./gradlew run
```

Look for `SubcomposeLayoutSubcompositionState` or `BoxWithConstraints` in the flame graph.

- [ ] **2. Add logging inside `BoxWithConstraints` or `SubcomposeLayout`.**

```kotlin
BoxWithConstraints {
    SideEffect { println("[Subcompose] BoxWithConstraints recomposed, maxWidth=$maxWidth") }
    // ...
}
```

If it logs on every minor resize tick, subcomposition is running too often.

For Desktop-specific measurement, read
[desktop-subcomposition-measurement.md](desktop-subcomposition-measurement.md).

### Phase 2 — Diagnose

- [ ] **3. Determine if subcomposition is necessary.** Ask: does the composition tree structure
  depend on the measured size?

| Situation | Needs subcomposition? | Alternative |
|-----------|----------------------|-------------|
| Change padding based on width | No | Conditional logic in composition phase. |
| Switch layout based on width | Maybe | Read window size from `LocalWindowInfo`. |
| Show/hide elements based on min width | Maybe | Pre-calculate breakpoints with `derivedStateOf`. |
| Intrinsics-dependent layout | Yes | `SubcomposeLayout` is the correct tool. |
| Embed a Swing component | Yes | `SwingPanel` uses subcomposition internally. |

For the decision tree, read [subcomposition-decision-tree.md](subcomposition-decision-tree.md).

- [ ] **4. Check for `BoxWithConstraints` overuse.** Common cases that do not need it:

```kotlin
// WRONG — subcomposition for simple padding
BoxWithConstraints {
    val padding = if (maxWidth > 600.dp) 32.dp else 16.dp
    Box(modifier = Modifier.padding(padding)) { /* ... */ }
}

// RIGHT — no subcomposition; read container width from LocalWindowInfo
val containerWidth = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}
val padding = if (containerWidth > 600.dp) 32.dp else 16.dp
Box(modifier = Modifier.padding(padding)) { /* ... */ }
```

- [ ] **5. Check `SwingPanel` resize behaviour.** `SwingPanel` subcomposes on every size change.
  If the Swing component does expensive layout on resize, the combination is costly.

For SwingPanel optimisations, read [swing-panel-subcomposition.md](swing-panel-subcomposition.md).

### Phase 3 — Fix

- [ ] **6. Replace `BoxWithConstraints` with derived state when possible.**

```kotlin
// BEFORE — subcomposes on every measure
@Composable
fun ResponsiveCard(content: @Composable () -> Unit) {
    BoxWithConstraints {
        if (maxWidth > 600.dp) WideCard(content) else NarrowCard(content)
    }
}

// AFTER — composes once, uses derived state or hoisted window width
@Composable
fun ResponsiveCard(windowWidth: Dp, content: @Composable () -> Unit) {
    if (windowWidth > 600.dp) WideCard(content) else NarrowCard(content)
}
```

- [ ] **7. Use `SubcomposeLayout` only for genuine intrinsics or two-pass needs.**

```kotlin
// GENUINE NEED — text wraps around an image whose size depends on available width
SubcomposeLayout { constraints ->
    val imagePlaceable = subcompose("image") { Image(...) }.first().measure(constraints)
    val textPlaceable = subcompose("text") { Text(...) }.first().measure(
        constraints.copy(maxWidth = constraints.maxWidth - imagePlaceable.width)
    )
    layout(constraints.maxWidth, max(imagePlaceable.height, textPlaceable.height)) {
        imagePlaceable.placeRelative(0, 0)
        textPlaceable.placeRelative(imagePlaceable.width, 0)
    }
}
```

- [ ] **8. Cache `SwingPanel` factory when possible.**

```kotlin
// WRONG — new factory on every recomposition
SwingPanel(factory = { JLabel(labelText) }, modifier = Modifier.size(200.dp, 100.dp))

// RIGHT — stable factory, update via property
val factory = remember { { JLabel() } }
SwingPanel(factory = factory, modifier = Modifier.size(200.dp, 100.dp),
    update = { it.text = labelText })
```

### Phase 4 — Verify

- [ ] **9. Re-run the profiler.** `SubcomposeLayout` / `BoxWithConstraints` should no longer
  dominate the flame graph.
- [ ] **10. Verify visual correctness.** The layout must behave identically.
- [ ] **11. Check `SwingPanel` does not allocate on every recomposition.**

## Patterns

### Avoid BoxWithConstraints for breakpoints

```kotlin
// WRONG — subcomposition on every measure
BoxWithConstraints {
    when {
        maxWidth < 600.dp -> CompactLayout()
        maxWidth < 840.dp -> MediumLayout()
        else -> ExpandedLayout()
    }
}

// RIGHT — observe window container size via LocalWindowInfo (standard Compose API)
// LocalWindowInfo.containerSize is IntSize in pixels; convert to Dp with LocalDensity.
val containerWidth = with(LocalDensity.current) {
    LocalWindowInfo.current.containerSize.width.toDp()
}
when {
    containerWidth < 600.dp -> CompactLayout()
    containerWidth < 840.dp -> MediumLayout()
    else -> ExpandedLayout()
}
```

### SubcomposeLayout for intrinsics

```kotlin
// RIGHT — genuine two-pass measurement need
SubcomposeLayout { constraints ->
    val header = subcompose("header") { Header() }.first().measure(constraints)
    val body = subcompose("body") { Body() }.first().measure(
        constraints.copy(maxHeight = constraints.maxHeight - header.height)
    )
    layout(constraints.maxWidth, header.height + body.height) {
        header.placeRelative(0, 0)
        body.placeRelative(0, header.height)
    }
}
```

## Mandatory rules

- **MUST** measure subcomposition cost before deciding to keep `BoxWithConstraints`.
- **MUST NOT** use `BoxWithConstraints` for simple conditional padding or margin that can be
  computed from hoisted state.
- **MUST** prefer derived state or direct conditional composition over subcomposition when the
  layout structure does not depend on measured size.
- **MUST** use `SubcomposeLayout` only for intrinsics-dependent layout or two-pass measurement.
- **MUST** provide a stable `factory` to `SwingPanel` and use `update` for dynamic properties.
- **MUST NOT** allocate new Swing components in the `SwingPanel` factory on every recomposition.

## Verification checklist

- [ ] Profiling confirms reduced `SubcomposeLayout`/`BoxWithConstraints` overhead after the fix.
- [ ] No `BoxWithConstraints` remains in a hot path without justification.
- [ ] `SwingPanel` uses a remembered factory and `update` for property changes.
- [ ] Visual layout is identical before and after replacement.
- [ ] Resize and scroll interactions are smooth.

## References

- [subcomposition-decision-tree.md](subcomposition-decision-tree.md) — when subcomposition is necessary
- [swing-panel-subcomposition.md](swing-panel-subcomposition.md) — factory stability, update pattern
- [desktop-subcomposition-measurement.md](desktop-subcomposition-measurement.md) — profiling on Desktop
