# Stability Inference Algorithm (Desktop)

The Compose compiler decides stability through a 12-phase algorithm. Understanding the phases helps
you interpret surprising verdicts in `classes.txt` and `composables.txt`.

## The 12 phases

1. **Well-known types** — primitives (`Int`, `Boolean`, etc.), `String`, `enum`, `Function`,
   inline classes, and standard-library stable types (`Unit`, `Nothing`, `Pair`, `Triple`) are
   hard-coded as stable.
2. **Marked types** — types annotated `@Stable` or `@Immutable` are stable.
3. **Configured types** — types listed in `stability_config.conf`.
4. **Mapped types** use explicit Kotlin-to-Compose stability mappings (such as `State` and `MutableState`).
5. **External types with metadata** — third-party Kotlin libraries compiled with the Compose
   compiler plugin emit a `$stable` field; the compiler trusts it.
6. **Collection types** — `kotlin.collections.*` interfaces (`List`, `Map`, `Set`, `Collection`,
   `MutableList`, etc.) are unstable unless explicitly mapped (they are interfaces, so the
   compiler cannot prove every implementation is immutable).
7. **Composable lambdas** — `@Composable` lambdas (`@dynamic`/`@static`) get special handling.
8. **Internal classes** — classes in the same module. The compiler inspects every field:
   - All `val` fields of stable types → `Stable`.
   - Any `var` field → `Unstable`.
   - Any field of a type the compiler cannot prove stable → `Unstable`.
9. **Java classes** — classes without Compose metadata. Default to `Unstable` unless a config
   file or `@Stable`/`@Immutable` annotation is present.
10. **Generic parameters** — unbounded generics default to `Unstable`.
11. **External types without metadata** — Kotlin or Java libraries compiled without the Compose
    compiler plugin. Default to `Unstable` unless configured otherwise.
12. **Runtime decision** — some types get a `$stable` field injected so the verdict is deferred
    to runtime. This is how `data class` with all-stable `val` properties becomes `runtime stable`.

## The `$stable` field

When a class is `runtime stable`, the compiler adds an `Int` companion constant named `$stable`.
Its value is a bitmask:

| Bit position | Meaning |
|-------------|---------|
| 0 | Class is stable overall (all fields stable). |
| 1…n | Per-field stability, only used for external types with metadata. |

You rarely need to read this field directly. The important takeaway is that `runtime stable` is
usually fine — it behaves like stable at runtime.

## When to worry about `runtime`

- If a `data class` has only `val` properties and the compiler reports `runtime stable`, no action
  is needed. This is normal.
- If a class is `runtime stable` and still causes excessive recompositions, check whether a parent
  class or generic parameter changed identity (`===` failed because a `List` was re-allocated).

## Strong Skipping interaction

Strong Skipping (default ON on Kotlin 2.0.20+) changes the skipability rules but **not** the
stability classification:

- Pre-Strong Skipping: a composable with any `unstable` parameter is non-skippable.
- Strong Skipping: every restartable composable is skippable, but `unstable` params are compared
  with `===` instead of `equals()`.

Under Strong Skipping, the stability label still matters because it decides *how* parameters are
compared, while non-skippable composables are rare (only `NonSkippableComposable` disables
skipping).
