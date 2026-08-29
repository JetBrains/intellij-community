# Compose Multiplatform and Jewel

Both stacks drive a Compose composition; what sits at the bottom differs. Jewel and Compose Multiplatform
render through Skia into a `ComposePanel`. Here the composition drives real Swing components.

In the IDE build the two share one `androidx.compose.runtime`, so everything the runtime owns ports
unchanged: `remember`, `mutableStateOf`, `derivedStateOf`, `LaunchedEffect`, `DisposableEffect`, snapshot
state and flow collection. Composable structure and state hoisting carry over with it.

## What has to be rewritten

- **Modifiers.** `androidx.compose.ui.Modifier` and `SwingModifier` are distinct types with distinct
  members; neither substitutes for the other. A modifier applied to a node that cannot take it fails with
  an error naming the expected and actual type.
  [MODIFIERS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/MODIFIERS.md)
  covers writing one.
- **Layout.** `Row`, `Column` and `Box` come from the separate `swing-ui-foundation` module, in
  `org.jetbrains.compose.swing.foundation.layout`, with their own scopes.
  [CUSTOM-CONTAINERS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/CUSTOM-CONTAINERS.md)
  covers writing a container.
- **Theming.** Jewel wraps its content in a theme that reads the IDE theme. Here there is nothing to wrap:
  styling reads the installed look and feel on every access. A change of look and feel shows at the next
  access. Drop the theme wrapper. Do not look for an equivalent.
- **Tests.** The harness is `runComposeSwingTest`, described in
  [TESTING-COMPONENTS.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/TESTING-COMPONENTS.md).
  Assertions written against a Skia-rendered semantics tree do not transfer; here the assertion target is
  the Swing component.

For how the composition maps onto a Swing tree at all, read
[ARCHITECTURE.md](https://github.com/JetBrains/compose-swing-ui/blob/v0.1.0-alpha06/docs/ARCHITECTURE.md) before
porting anything non-trivial.
