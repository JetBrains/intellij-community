---
name: code-style
description: Apply IntelliJ Kotlin and Java code style when writing or reviewing.
---

# Code Style

Follow the IntelliJ Coding Guidelines with these IntelliJ-specific rules.

## Language

- Use Kotlin for new files unless the user explicitly requests Java.
- Keep existing Java files in Java unless the task requires a language conversion.
- Before writing or reviewing Java, read [Modern Java](references/modern-java.md).
- Use idiomatic Kotlin: extension functions, data classes, null safety
- Mark experimental APIs with `@ApiStatus.Experimental`
- **Build scripts Kotlin style**: Follow `community/platform/build-scripts/kotlin-style-recommendations.md` for build-scripts code.

## Sealed hierarchies

- Before you list several subtypes in a `when` branch, an `is` check, or a `catch`, open the declaration of the type.
- If a common supertype covers exactly those subtypes, use the supertype. It stays correct when someone adds a new subtype.
- Check the value of a sealed type or an enum with an exhaustive `when`. Do not use an `is` check, an `as?` cast, an `==` comparison, or `else`. In Eel and IJent code, this is a rule, also for a `private` type. See `community/platform/eel/docs/api/exhaustive-when.md`.

## Formatting

- Read every applicable `.editorconfig` before you review or change formatting.
- Use the effective `.editorconfig` value for each setting. If no file defines a setting, use these defaults:
  - Indentation: 2 spaces (4 for Go files)
  - Line length: 140 characters max
  - Braces: `else`, `catch`, and `finally` on new lines
- In `/rustrover` directory, except `/rustrover/build`, you must always use standard Kotlin formatting rules with 4 spaces indentation.

## Threading annotations

- When you add `@RequiresEdt`, `@RequiresBackgroundThread`, `@RequiresReadLock`, `@RequiresWriteLock`,
  or `@RequiresReadLockAbsence`, do not pass `generateAssertion`. Keep the default value unless the user explicitly requests a different one.
- Never write `generateAssertion = false /* IJPL-115548 */`. It is a legacy mass-migration marker. Do not copy it from existing code.
- Set `generateAssertion = false` only when the user explicitly requests it, or when the runtime assertion is known to fail. State the reason in a comment.

## Error handling

- In a `catch` block for `Throwable` or `Exception`, call `com.intellij.diagnostic.rethrowControlFlowException(e)` first.
  It rethrows a control flow exception, such as `CancellationException`. A control flow exception must never reach the log.
- Do not catch or rethrow `CancellationException` explicitly, and do not test the exception type by hand.
- In Java, static-import `com.intellij.diagnostic.ControlFlowExceptionsKt.rethrowControlFlowException`.
- Do not use the deprecated `com.intellij.openapi.diagnostic.rethrowControlFlowException`.

```kotlin
try {
  doWork()
}
catch (e: Throwable) {
  rethrowControlFlowException(e)
  LOG.error(e)
}
```
