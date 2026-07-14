# Reading composables.txt

`composables.txt` lists every `@Composable` function in the compiled module, with its full
signature and a per-parameter stability annotation.

## File structure

```
restartable scheme("[androidx.compose.ui.UiComposable]") fun ComposableName(
  stable param1: Type1,
  unstable param2: Type2,
  runtime param3: Type3,
  @dynamic param4: @Composable () -> Unit,
  @static param5: @Composable () -> Unit,
)
```

Each function block starts with `restartable` or `skippable restartable`.

## Prefixes on parameters

| Prefix | Meaning | Skipability impact |
|--------|---------|-------------------|
| `stable` | Type is known immutable. | Enables full `equals()` comparison. |
| `unstable` | Type is mutable or unknown. | Blocks skipping (pre–Strong Skipping) or falls back to `===` (Strong Skipping). |
| `runtime` | Stability decided by a `$stable` field at runtime. | Uses `$stable` bitmask; usually behaves like stable. |
| `@static` | A `@Composable` lambda with no captured state. | Treated as stable. |
| `@dynamic` | A `@Composable` lambda that captures state. | Treated as unstable. |

## Key lines to search for

```bash
# Find every restartable but non-skippable composable
grep -n "restartable" composables.txt | grep -v "skippable"

# Find a specific function
grep -n -A 10 "fun MyComposable" composables.txt
```

## What counts as non-skippable?

- `restartable` without `skippable` before it → non-skippable. Every parent recompose forces this
  one to recompose too.
- `skippable restartable` → the compiler generated a `changed` mask; Compose can skip it if no
  input changed.

## What to ignore

- `restartable` composables that are `skippable` but contain one `unstable` parameter are fine
  under Strong Skipping — they fall back to `===` for that parameter only.
- Composables called once at startup (e.g. `App()`, `RootContent()`) do not need to be skippable.
