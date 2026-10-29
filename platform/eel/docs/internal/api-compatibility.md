# API Binary Compatibility Rules

**Summary:** do not use Kotlin `data class` declarations or Kotlin default arguments in API, even in declarations marked `@ApiStatus.Internal`.

This rule applies to every public, `@ApiStatus.Experimental`, and `@ApiStatus.Internal` declaration in Eel and IJent modules. Other modules and plugins compile against these declarations separately. Binary-incompatible changes break them at runtime with `NoSuchMethodError` or `IncompatibleClassChangeError`.

## Do Not Use Kotlin Default Arguments in API

Do not add default parameter values to functions or constructors in API declarations. Do not add a new parameter with a default value to an existing API function.

```kotlin
// Incorrect: default argument in an API interface or class
abstract class WslIjentManager {
  open suspend fun closeIjentSession(wslDistribution: WSLDistribution, rootUser: Boolean = false) = Unit
}
```

When a function has a default argument, the Kotlin compiler generates a synthetic `$default` method with a bitmask parameter in the JVM bytecode. Adding, removing, or reordering parameters changes the signature of both the method and the `$default` bridge. Code compiled against an older version fails at runtime with `java.lang.NoSuchMethodError`.

Instead, write explicit function or constructor overloads, or use `@GeneratedBuilder` option builders:

```kotlin
// Correct: explicit overloads without default arguments
abstract class WslIjentManager {
  open suspend fun closeIjentSession(wslDistribution: WSLDistribution, rootUser: Boolean) = Unit

  suspend fun closeIjentSession(wslDistribution: WSLDistribution) {
    closeIjentSession(wslDistribution, rootUser = false)
  }
}
```

See the Kotlin backward compatibility guidelines: [Avoid adding arguments to existing API functions](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-adding-arguments-to-existing-api-functions).

## Do Not Use Kotlin `data class` in API

Do not declare a `data class` in an API package, even if the class is marked `@ApiStatus.Internal`.

```kotlin
// Incorrect: data class and default argument in API
data class DockerConfig(
  val containerId: String,
  val endpoint: String? = null,
)
```

The Kotlin compiler generates `copy(...)` and `componentN()` methods for every `data class`:

- Adding or removing a property changes the signature of the constructor and the generated `copy(...)` method, which breaks binary compatibility.
- Changing the order of properties changes what `component1()`, `component2()`, and destructuring declarations return, which breaks behavioral compatibility.

Instead, declare a regular `class` with explicit constructors, `equals`, `hashCode`, and `toString`:

```kotlin
// Correct: regular class with explicit overloads and value methods
class DockerConfig(
  val containerId: String,
  val endpoint: String?,
) {
  constructor(containerId: String) : this(containerId, null)

  override fun equals(other: Any?): Boolean =
    this === other || (other is DockerConfig && containerId == other.containerId && endpoint == other.endpoint)

  override fun hashCode(): Int = 31 * containerId.hashCode() + endpoint.hashCode()

  override fun toString(): String = "DockerConfig(containerId=$containerId, endpoint=$endpoint)"
}
```

See the Kotlin backward compatibility guidelines: [Avoid using data classes in your API](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#avoid-using-data-classes-in-your-api).
