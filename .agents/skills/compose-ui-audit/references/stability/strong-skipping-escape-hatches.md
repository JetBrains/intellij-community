# Strong Skipping Escape Hatches

Kotlin 2.0.20+ enables Strong Skipping by default. It makes every restartable composable skippable,
but changes how unstable parameters are compared. Occasionally you need to override this behaviour.

## Three annotations

### `@DontMemoize`

```kotlin
@DontMemoize
@Composable
fun LoggedButton(onClick: () -> Unit) { /* ... */ }
```

Prevents the compiler from auto-remembering capturing lambdas inside this composable. Use when the
lambda captures something that must be re-evaluated on every recomposition (rare).

**Do not use this for performance.** It disables an optimisation. Only use when correctness
requires fresh lambda evaluation.

### `@NonSkippableComposable`

```kotlin
@NonSkippableComposable
@Composable
fun AlwaysRunSideEffect() { /* ... */ }
```

Forces the composable to be non-skippable even under Strong Skipping. Use when:
- The composable's body must execute on every parent recompose (e.g. to refresh a side effect).
- Skipping would break external observers.

**Do not use this for performance.** It is the opposite of a performance fix.

### `@NonRestartableComposable`

```kotlin
@NonRestartableComposable
@Composable
fun InlineHint(text: String) { /* ... */ }
```

Prevents the composable from being a recomposition boundary. The compiler inlines it into its
caller. Use for tiny leaf composables that do not benefit from their own restart group.

**Prefer to let the compiler decide.** Only apply this when profiling shows the restart-group
overhead is measurable.

## Auto-remember of lambdas

Under Strong Skipping, lambdas declared inside a `@Composable` function are automatically wrapped
in `remember`:

```kotlin
@Composable
fun Parent() {
    val onClick = { /* auto-remembered */ }  // stable identity
    Child(onClick)
}
```

This does **not** apply inside `LazyListScope.items { }` or other non-`@Composable` scopes:

```kotlin
LazyColumn {
    items(data) { item ->
        val onClick = { /* NOT auto-remembered */ }  // new instance every recompose
        Row(onClick = onClick)
    }
}
```

Fix: use a method reference (`vm::onSelect`) or hoist the lambda outside the `items` block.

## When to leave Strong Skipping alone

Strong Skipping is the correct default. Disable or override it only when:
- A lambda capture genuinely must re-evaluate (`@DontMemoize`).
- A composable must run for side effects (`@NonSkippableComposable`).
- Profiling shows restart-group overhead on a tiny leaf (`@NonRestartableComposable`).

Do not add these annotations speculatively.
