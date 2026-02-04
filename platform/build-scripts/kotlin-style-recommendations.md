# Kotlin Style Recommendations for IntelliJ Codebase

## Data Classes

- **Use `@JvmField` for internal data class fields** - improves Java interop performance by avoiding getter/setter generation

```kotlin
// Preferred
internal data class CachedModuleInfo(
  @JvmField val name: String,
  @JvmField val loading: ModuleLoadingRuleValue?,
  @JvmField val sourceModuleSet: String,
)

// Avoid
internal data class CachedModuleInfo(
  val name: String,
  val loading: ModuleLoadingRuleValue?,
  val sourceModuleSet: String,
)
```

## Map/Set Operations

- **Use `.put()/.get()` instead of `[]` operator** - explicit method calls are preferred

```kotlin
// Preferred
result.put(key, value)
result.get(key)

// Avoid
result[key] = value
result[key]
```

## Collection Initialization

- **Use explicit `LinkedHashMap`/`LinkedHashSet` or `HashMap`/`HashSet`** instead of `mutableMapOf()`/`mutableSetOf()`

```kotlin
// Preferred
val result = LinkedHashMap<String, CachedModuleInfo>()
val seen = HashSet<String>()

// Avoid
val result = mutableMapOf<String, CachedModuleInfo>()
val seen = mutableSetOf<String>()
```

## Function Body Style

- **Don't use expression body if the body is multiline** - use block body with explicit `return` instead

```kotlin
// Preferred
fun process(items: List<Item>): Result {
  return items
    .filter { it.isValid }
    .map { transform(it) }
    .toResult()
}

// Avoid
fun process(items: List<Item>): Result =
  items
    .filter { it.isValid }
    .map { transform(it) }
    .toResult()
```

## Rationale

1. `@JvmField` eliminates property accessor overhead when accessed from Java code
2. Explicit `.put()/.get()` makes the intent clearer and avoids ambiguity with nullable returns
3. Explicit collection types make memory layout and iteration order explicit (LinkedHashMap preserves insertion order, HashMap does not)
4. Block body with explicit `return` is more readable for multiline expressions and makes debugging easier
