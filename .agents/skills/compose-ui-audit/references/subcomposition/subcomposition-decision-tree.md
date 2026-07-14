# Subcomposition Decision Tree

## What is subcomposition?

Subcomposition runs `@Composable` functions during the layout/measure phase instead of the normal
composition phase. It is necessary when the UI structure depends on measurements that are only
known at layout time.

## Common APIs that use subcomposition

| API | Uses subcomposition | Typical reason |
|-----|--------------------|---------------|
| `BoxWithConstraints` | Yes | Exposes `maxWidth`/`maxHeight` to composable body. |
| `SubcomposeLayout` | Yes | Full control over composition inside measure. |
| `SwingPanel` | Yes | Embeds Swing components sized by Compose layout. |
| `LazyColumn`/`LazyRow` | Yes internally | Items are composed on demand based on viewport. |
| `AndroidView` | N/A on Desktop | Not applicable. |

## Decision tree

```
Does the UI structure depend on measured size?
├── No → Use standard modifiers (Column, Row, Box, padding, weight)
│
└── Yes
    ├── Is it just breakpoints (compact/medium/expanded)?
    │   ├── Can breakpoints be derived from window state? → Read LocalWindowInfo.current.containerSize
    │   │   (convert pixels→Dp via LocalDensity); use derivedStateOf for the breakpoint value
    │   └── Must depend on parent container size? → BoxWithConstraints is acceptable
    │
    ├── Is it intrinsics (text wrapping around image, etc.)?
    │   └── Yes → SubcomposeLayout is the correct tool
    │
    └── Is it Swing interop?
        └── Yes → SwingPanel (unavoidable subcomposition)
```

## Rules

- **Never** use `BoxWithConstraints` for simple conditional values that can be computed from
  external state.
- **Always** justify `SubcomposeLayout` with a specific intrinsics or two-pass need.
- **Accept** `SwingPanel` subcomposition for Swing interop, but minimise factory recreation.
