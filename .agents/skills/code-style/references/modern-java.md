# Modern Java

Use modern Java for new and changed code within the target module's compatibility limits.
Use nearby code to understand behavior, not as a reason to repeat outdated idioms.
Keep unrelated code unchanged.

## Compatibility

- Check the module's effective language level and Java API target, including inherited build settings.
- The installed JDK is not the compatibility target. Do not assume that every module supports Java 21.
- Use stable features supported by that target.
  Keep existing language levels and build settings unchanged unless the task requires an upgrade.
- Do not enable preview features for a style change. Check the target release documentation before using features absent from the table.
- Preserve public contracts, observable behavior, evaluation order, exception behavior, and null handling.

## Features

The table lists the first stable release for each feature, not its first preview.
Check the [language release guide][language] and the target release's API documentation for other features.

| Stable from | Prefer | Use when |
| --- | --- | --- |
| 9 | `List.of`, `Set.of`, `Map.of`, `Map.ofEntries`, `Map.entry` | Creating small unmodifiable collections. |
| 10 | `var` | Declaring local variables, subject to the exceptions below. |
| 10 | `List.copyOf`, `Set.copyOf`, `Map.copyOf` | Taking an unmodifiable snapshot. |
| 14 | Switch expressions and arrow cases | Selecting a value or avoiding unnecessary fall-through. |
| 15 | Text blocks | Representing multiline text without repeated escapes and concatenation. |
| 16 | Records, including local records | Defining data carriers with value equality. |
| 16 | Pattern matching for `instanceof` | Testing a type and then accessing the typed value. |
| 17 | Sealed classes and interfaces | Modeling a deliberately closed hierarchy. |
| 21 | Record patterns and pattern matching for `switch` | Deconstructing records or handling alternatives by type. |
| 21 | Sequenced collections | Accessing first or last elements, or reversed views, in encounter order. |
| 22 | Unnamed variables and patterns (`_`) | Declaring a value or matching a component that is deliberately unused. |
| 25 | Flexible constructor bodies | Validating or preparing arguments before `super(...)` or `this(...)`. |

### Local variables

Default to `var` where supported.
Use an explicit type when Java requires it, when it preserves semantics, or when it materially improves readability.
Check generic inference, primitive widths, overload selection, and later assignments before replacing a declared type.
Keep explicit types in fields, parameters, and return declarations.

### Data and control flow

- Prefer records for new value carriers.
  For existing classes, first check accessors, equality, serialization, reflection, and inheritance requirements.
- Records do not make referenced objects immutable. Preserve defensive copies where required.
- Seal a hierarchy only when its extension contract permits it. Do not close an existing public extension point for style.
- Prefer exhaustive switches over closed alternatives. Preserve deliberate fall-through and existing behavior for `null`.
- Use record patterns where deconstruction makes the code clearer. Keep a named value when the code needs the whole record.
- Use `_` only when the value is unused. Keep required validation, side effects, and error handling.
- For `MethodHandle.invokeExact`, preserve the static argument types and the return cast, even when the result is unused.
  A standalone invocation has the return type `void`, which can cause `WrongMethodTypeException`.
- Preserve the exact whitespace and trailing newline when replacing string literals with text blocks.
- Use flexible constructor bodies only where supported. Follow their restrictions on access to the instance under construction.

### Collections

Check the [collection guide][collections] and the [List API][list-api] before replacing a collection idiom.

- Factory methods and `copyOf` produce unmodifiable collections. Keep a mutable implementation when callers need mutation.
- These factories reject null elements, keys, or values. `Set.of` rejects duplicate elements, and map factories reject duplicate keys.
- `Set.of` and `Map.of` do not preserve insertion order. Keep an ordered implementation when iteration order matters.
- A snapshot differs from an unmodifiable view of a mutable collection. Preserve whether later source changes remain visible.
- Unmodifiable collections do not make their elements immutable.
- Check empty-collection behavior before replacing indexed access with `getFirst()` or `getLast()`.
- A `reversed()` result is a view, not an independent copy. Preserve the required relationship to the source collection.

### Concurrency

Virtual threads are not a general replacement for IntelliJ executors or coroutines.
Keep platform threading APIs, EDT requirements, read/write locks, cancellation, and lifecycle ownership.
Before changing coroutine scope ownership, read the [coroutine scope rules](../../platform-coroutines-structured-concurrency/SKILL.md).
Use virtual threads only when the target supports them and the task's concurrency design requires them.

## Examples

Apply each example only when the target supports it and the behavior checks above hold.

### Generic inference

Before:

```text
List<String> names = new ArrayList<>();
```

Prefer:

```java
var names = new ArrayList<String>();
```

Using `var names = new ArrayList<>();` instead infers `ArrayList<Object>` and loses the element type.
Keep constructor type arguments only when inference would otherwise change the required type.

### Unused method handle result

Before:

```text
int ignored = (int)handle.invokeExact(argument);
```

Prefer on Java 22 or later:

```java
var _ = (int)handle.invokeExact(argument);
```

Keep the named local on older targets.
Do not replace the assignment with `handle.invokeExact(argument);`.
That call expects a `void` return type instead of `int`.

### Type test and cast

Before:

```text
if (value instanceof String) {
  String text = (String)value;
  return text.length();
}
```

Prefer:

```java
if (value instanceof String text) {
  return text.length();
}
```

### Value-returning switch

Before:

```text
switch (exitCode) {
  case 0: return Result.SUCCESS;
  default: return Result.FAILURE;
}
```

Prefer:

```java
return switch (exitCode) {
  case 0 -> Result.SUCCESS;
  default -> Result.FAILURE;
};
```

### Value carrier

For a new internal value type, avoid this class boilerplate:

```text
final class ExitCode {
  private final int value;

  ExitCode(int value) {
    this.value = value;
  }

  int value() {
    return value;
  }
}
```

Prefer:

```java
record ExitCode(int value) { }
```

The record also defines value equality, hashing, and a string representation.
This is not an automatic replacement for an existing class with identity semantics.

### Multiline text

Before:

```text
String payload = "{\n  \"enabled\": true\n}\n";
```

Prefer:

```java
var payload = """
  {
    "enabled": true
  }
  """;
```

### Collection creation

Before:

```text
List<String> names = Collections.unmodifiableList(Arrays.asList("alpha", "beta"));
```

Prefer on Java 10 or later:

```java
var names = List.of("alpha", "beta");
```

On Java 9, keep the explicit variable type and use `List.of`.

## Source and adaptations

Adapted from Ken Kousen's [modernize-java skill][upstream] at revision `aec3f82a47ac27928732de61ea8e58f4e192eeb5`.
The original MIT notice is in [modern-java.LICENSE.txt](modern-java.LICENSE.txt).

This adaptation adds feature versions, examples, and IntelliJ constraints.
It makes `var` the default and replaces blanket advice about virtual threads.
It covers writing and reviewing Java, not only refactoring. Repository instructions define how to build and test the affected modules.
Update the pinned source only as part of a reviewed change.

[upstream]: https://github.com/kousen/claude-code-training/blob/aec3f82a47ac27928732de61ea8e58f4e192eeb5/skills/modernize-java/SKILL.md
[language]: https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html
[collections]: https://docs.oracle.com/en/java/javase/25/core/creating-immutable-lists-sets-and-maps.html
[list-api]: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
