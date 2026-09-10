# Stabilising Types — Three-Tier Waterfall

When `classes.txt` says a type is unstable and you have confirmed it causes real recompositions,
apply fixes in this order. Each tier is more invasive than the last; stop as soon as the problem
is solved.

## Tier 1 — Restructure (preferred: no annotation)

Make the type inherently stable by removing mutability.

| Problem | Fix |
|---------|-----|
| `var` field | Change to `val`. If the field must change, use `State<T>` or `MutableState<T>` and pass the state object, which is stable. |
| `List<T>` property | Change to `kotlinx.collections.immutable.ImmutableList<T>` or `PersistentList<T>`. |
| `Map<K,V>` / `Set<T>` | Same as above: use `kotlinx.collections.immutable` equivalents. |
| Unbounded generic `<T>` | Add `: Any` or a stable upper bound, or wrap the value in a stable container. |
| Mutable collection allocated in composable | Allocate once (file-level `val`) or `remember` it. |

Example — mutable `var`:

```kotlin
// BEFORE — unstable because of var
class Settings(val name: String, var isEnabled: Boolean)

// AFTER — stable, state hoisted upward
class Settings(val name: String, val isEnabled: State<Boolean>)
```

## Tier 2 — Annotate (when you own the source)

Add `@Immutable` or `@Stable` to the class declaration. **Only do this if the contract is true.**

| Annotation | When to use | When NOT to use |
|------------|-------------|-----------------|
| `@Immutable` | Every property is a `val` of an already-immutable type. The class and all nested types are deeply immutable. | Any property is a `var`, `vararg`, `lateinit`, or mutable collection. |
| `@Stable` | The type is mutable but changes are notified through `Snapshot` (e.g. `MutableState`, `SnapshotStateList`). The type implements equality correctly (`equals()`/`hashCode()`). | The type has a `var` that changes without Snapshot awareness. |

Example — `@Immutable`:

```kotlin
@Immutable
data class UserProfile(
    val id: String,
    val displayName: String,
    val avatarUrl: String,
)
```

Example — `@Stable` (rare on Desktop):

```kotlin
@Stable
class FormState {
    private val _fields = mutableStateListOf<Field>()
    val fields: List<Field> = _fields
    // mutations go through SnapshotStateList — Snapshot is aware
}
```

## Tier 3 — Configure (when you do NOT own the source)

Use `stability_config.conf` for third-party Java or Kotlin types.

```
// In stability_config.conf
com.example.ThirdPartyModel
com.example.LegacyDto
```

Then wire it in `build.gradle.kts`:

```kotlin
composeCompiler {
    stabilityConfigurationFile = rootProject.file("stability_config.conf")
}
```

Only add types you have verified are logically immutable. Adding a mutable type here breaks
recomposition correctness silently.

## What NOT to do

- Do not annotate a `data class` that the compiler already infers as stable. It adds noise and
  risks staleness.
- Do not annotate a type with `var` as `@Immutable`. The compiler may trust you and skip
  recompositions when it should not.
- Do not add `@Stable` to a Java class just because you think it is stable. Use the config file
  instead.
