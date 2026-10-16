# Audit Search Patterns

Use these grep/rg patterns to locate code to inspect during an audit. Search hits are **leads**,
not findings — always read the surrounding context before reporting.

## Core Compose patterns

```
@Composable
remember\(
rememberSaveable
derivedStateOf
LaunchedEffect
DisposableEffect
SideEffect
produceState
snapshotFlow
rememberUpdatedState
rememberCoroutineScope
\.launch \{
GlobalScope
Thread\(
```

## State and lazy lists

```
LazyColumn
LazyRow
items\(
itemsIndexed\(
contentType
key =
mutableStateOf
mutableIntStateOf
mutableStateListOf
MutableState<
State<
```

## Composable API hygiene and static-rule leads

These patterns are executable with `rg -U -f <pattern-file>`. Keep explanations outside the block so `rg -f` does not
interpret comments as literal pattern text.

```regex
(?s)@Composable(?:\([^)]*\))?\s+(?:(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?|private|internal|public|protected|inline|operator|infix|suspend)\s+)*fun\s+[a-z]
(?s)@Composable(?:\([^)]*\))?\s+(?:(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?|private|internal|public|protected|inline|operator|infix|suspend)\s+)*fun\s+[A-Z][A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*[^\n{]+
MutableState<
State<
MutableList<
MutableMap<
MutableSet<
ArrayList<
List<
Map<
Set<
@ReadOnlyComposable
movableContentOf
@Preview
compositionLocalOf
staticCompositionLocalOf
GlobalScope
Thread\.sleep
runCatching\s*\{
```

Follow up manually before reporting:

- Unit-returning content composables that match the lowercase `fun` pattern should usually be PascalCase.
- Value-returning composables that match the uppercase return-type pattern should usually be camelCase or become
  non-composable helpers/state holders, depending on whether they read composition.
- `List`/`Map`/`Set` are stability leads, not automatic findings; check hot-path usage and stability configuration.
- `State<T>`/`MutableState<T>` are fine inside state holders, but usually wrong as reusable composable parameters.
- `@ReadOnlyComposable` is only suspicious when the body emits UI, launches effects, or mutates composition.
- `runCatching` matters in suspending/UI-reached code when it can swallow `CancellationException`.

## Modifier patterns

```
Modifier\.composed
Modifier\.offset\(
Modifier\.alpha\(
Modifier\.rotate\(
Modifier\.scale\(
modifier\s*:\s*Modifier
\bm\s*:\s*Modifier
```

## Animation

```
Animatable\(
animate.*AsState
updateTransition\(
AnimatedContent\(
AnimatedVisibility\(
SharedTransitionLayout
sharedElement\(
sharedBounds\(
sharedElementWithCallerManagedVisibility\(
rememberSharedContentState\(
MutableTransitionState\(
rememberInfiniteTransition
animateTo\(
animateDecay\(
snapTo\(
animateContentSize\(
animateItem\(
```

When these appear, check the desktop-safe animation reference for API choice, remembered animation state,
target-driven effects, phase-correct reads, shared element keys/scopes/modifier order, `contentKey`, and
offscreen infinite-transition risks.

## CompositionLocals

```
CompositionLocal
compositionLocalOf
staticCompositionLocalOf
```

## Inline data work (flag in composable bodies)

O(N) string operations — allocate proportional to input:
```
\.split\(
\.lines\(
\.lineSequence\(
\.trimIndent\(
\.replace\(
\.format\(
\.substringBefore
\.substringAfter
```

Regex construction or execution in composition:
```
Regex\(
\.toRegex\(
\.matches\(
\.find\(
```

O(N) collection traversals:
```
\.count\(
\.joinToString\(
\.mapIndexed\(
\.flatMap\(
\.sumOf\(
\.maxOf\(
\.minOf\(
```

Note: trivially cheap reads (`.size`, `.length`, `.isEmpty()`, index access) are fine.

## IO operations (strictly forbidden in composable bodies — see boundary-violations.md)

```
Json\.
Gson
Moshi
ObjectMapper
HttpClient
OkHttp
Retrofit
URL\(
Socket\(
File\(
Files\.
FileInputStream
BufferedReader
DriverManager
Connection
prepareStatement
executeQuery
ProcessBuilder
Runtime\.getRuntime
```

## App-state leaks

```
app-level state/store/client types and file/path/network APIs inside ui/ source directories
```
