# Compose Stability — Diagnosis and Fix Workflow

**Core principle:** Measure → Optimise → Measure. Do not add `@Stable` or `@Immutable`
annotations blindly. Confirm the instability causes a real problem first.

> **Routing:** Use this workflow when an audit finding points to excessive recompositions due to
> unstable parameters, or when the developer mentions compiler reports, non-skippable composables,
> or type stability.

## Workflow

### Phase 1 — Measure (establish evidence before changing code)

- [ ] **1. Enable Compose Compiler reports.** Add to the module's `build.gradle.kts`:

```kotlin
composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}
```

- [ ] **2. Build the target variant.**

```bash
./gradlew assemble
```

- [ ] **3. Locate the output files.**

```
build/compose_compiler/
├── <module>_release-classes.txt
├── <module>_release-composables.txt
├── <module>_release-composables.csv
└── <module>_release-module.json
```

For Desktop/JVM builds the suffix may differ; look for the most recent files.

- [ ] **4. Read `composables.txt` first.** Search for `restartable` lines that are **not**
  followed by `skippable`. Each one is a recomposition entry point that cannot be skipped.
  Per-parameter prefixes: `stable`, `unstable`, `runtime`, `@static`, `@dynamic`. Any `unstable`
  parameter blocks skipping.

For the full grammar, read [reading-composables-txt.md](reading-composables-txt.md).

- [ ] **5. Read `classes.txt` to learn *why* a class is unstable.** For every type flagged
  `unstable` in `composables.txt`, find its declaration in `classes.txt`.

For the full grammar, read [reading-classes-txt.md](reading-classes-txt.md).

- [ ] **6. Open `module.json` for triage numbers.** Use counts to compare before/after a fix or
  to decide which module to attack first. Do not treat counts as a KPI.

- [ ] **7. Confirm the problem is real.**

```kotlin
@Composable
fun SuspectItem(data: ItemData) {
    SideEffect { println("SuspectItem recompose: ${data.id}") }
    // ...
}
```

Reproduce the user journey. If the log prints on every scroll tick or animation frame, you have
a real problem.

For Desktop-specific measurement techniques, read [desktop-measurement.md](desktop-measurement.md).

### Phase 2 — Diagnose

- [ ] **8. Prioritise by hot path × frequency.** A recomposition in a `LazyColumn` row beats a
  10× recomposition on a one-off settings screen.

- [ ] **9. Cross-reference with Strong Skipping.** On Kotlin 2.0.20+, Strong Skipping makes every
  restartable composable skippable by default, but unstable params are compared with `===`
  instead of `equals`. A `listOf(...)` allocated fresh every recompose fails `===`.
  Lambdas inside `LazyListScope.items { }` are **not** auto-remembered.

For Strong Skipping edge cases and escape hatches, read
[strong-skipping-escape-hatches.md](strong-skipping-escape-hatches.md).

- [ ] **10. For surprising verdicts (`runtime`, `unknown`), read the inference deep-dive.**

For the algorithm walkthrough, read
[stability-inference-algorithm.md](stability-inference-algorithm.md).

### Phase 3 — Fix

- [ ] **11. Choose the right tier.** Three-tier strategy, in order:

  1. **Restructure** — make the type truly stable (`val` + immutable fields). No annotation needed.
  2. **Annotate** — add `@Immutable` or `@Stable` when you own the source and the contract is
     honoured.
  3. **Configure** — add the type to `stability_config.conf` for third-party or Java types.

For the full stabilisation guide, read [stabilising-types.md](stabilising-types.md).
For the config file grammar, read [stability-config-syntax.md](stability-config-syntax.md).

- [ ] **12. Re-build and re-read the reports.** The previously `unstable` parameter should now
  read `stable` or `runtime`. Re-run the logging test to confirm recompositions dropped.

### Phase 4 — Verify

- [ ] **13. Consider a CI stability gate.** Fails the build when a previously-skippable
  composable becomes non-skippable.

For CI gate setup, read [ci-stability-gate.md](ci-stability-gate.md).

## Patterns

### Do not annotate without evidence

```kotlin
// WRONG — SettingsItem is already inferred stable; @Immutable adds noise
@Immutable
data class SettingsItem(val name: String, val value: Boolean)

// RIGHT — let the compiler infer it
data class SettingsItem(val name: String, val value: Boolean)
```

### `List<T>` parameter blocks skipping

```kotlin
// WRONG — List is an interface; compiler cannot prove immutability
@Composable fun ItemList(items: List<Item>) { /* ... */ }

// RIGHT
@Composable fun ItemList(items: ImmutableList<Item>) { /* ... */ }
```

### Strong Skipping + fresh list literal

```kotlin
// WRONG — listOf allocates fresh every recompose; fails === under Strong Skipping
@Composable fun Header() { ActionRow(actions = listOf(Action.Share, Action.Save)) }

// RIGHT — stable identity
private val HeaderActions = persistentListOf(Action.Share, Action.Save)
@Composable fun Header() { ActionRow(actions = HeaderActions) }
```

### Lambda inside LazyListScope.items is not auto-remembered

```kotlin
// WRONG — lambda in LazyListScope is not auto-remembered
LazyColumn {
    items(snacks, key = { it.id }) { snack ->
        SnackRow(snack, onClick = { vm.select(snack.id) })
    }
}

// RIGHT — method reference is stable
LazyColumn {
    items(snacks, key = { it.id }) { snack ->
        SnackRow(snack, onClick = vm::select)
    }
}
```

## Mandatory rules

- **MUST** measure before optimising. A compiler report alone is not evidence of a real problem.
- **MUST** confirm excessive recompositions with logging or profiling before stabilising a type.
- **MUST NOT** add `@Stable` or `@Immutable` to a type whose contract you cannot guarantee.
- **MUST NOT** chase 100% skippability. Skippability is a diagnostic, not a KPI.
- **MUST** read `composables.txt` per-parameter annotations, not just the function name.
- **MUST** cross-reference unstable params back to `classes.txt` to identify the root cause.
- **MUST** prefer `@Immutable` over `@Stable` whenever every property is a `val` of an
  already-immutable type.
- **MUST** prefer `kotlinx.collections.immutable` over annotating a mutable collection as stable.
- **MUST NOT** use `listOf()`/`mapOf()`/`setOf()` inside a hot composable body as a parameter.

## Verification checklist

- [ ] `composeCompiler { reportsDestination = ... }` configured and build succeeds.
- [ ] All four report files exist.
- [ ] At least one unstable parameter site identified in `composables.txt`.
- [ ] Each unstable parameter traced back to `classes.txt` for root cause.
- [ ] Logging or profiling confirms excessive recompositions in a hot path.
- [ ] After the fix, the parameter reports `stable` or `runtime`.
- [ ] After the fix, logging confirms recomposition count dropped.
- [ ] No `@Stable`/`@Immutable` added to a type that still contains a `var` or unstable nested type.

## References

- [reading-composables-txt.md](reading-composables-txt.md) — composables.txt grammar
- [reading-classes-txt.md](reading-classes-txt.md) — classes.txt grammar
- [stability-inference-algorithm.md](stability-inference-algorithm.md) — 12-phase algorithm
- [stabilising-types.md](stabilising-types.md) — three-tier waterfall
- [stability-config-syntax.md](stability-config-syntax.md) — config grammar
- [strong-skipping-escape-hatches.md](strong-skipping-escape-hatches.md) — escape hatches
- [desktop-measurement.md](desktop-measurement.md) — Desktop measurement
- [ci-stability-gate.md](ci-stability-gate.md) — CI gate setup
