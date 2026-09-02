---
name: code-style
description: Apply IntelliJ Kotlin and Java code style when writing or reviewing.
---

# Code Style

Follow the IntelliJ Coding Guidelines with these IntelliJ-specific rules.

## Language

- **ALWAYS use Kotlin for new files** - Only use Java when modifying existing Java code
- Use idiomatic Kotlin: extension functions, data classes, null safety
- Mark experimental APIs with `@ApiStatus.Experimental`
- **Build scripts Kotlin style**: Follow `community/platform/build-scripts/kotlin-style-recommendations.md` for build-scripts code:
  - Use `@JvmField` for internal data class fields
  - Use `.put()`/`.get()` instead of `[]` operator
  - Use explicit `HashMap`/`HashSet`/`LinkedHashMap` instead of `mutableMapOf()`/`mutableSetOf()`

## Formatting

- Read every applicable `.editorconfig` before you review or change formatting.
- Use the effective `.editorconfig` value for each setting. If no file defines a setting, use these defaults:
  - Indentation: 2 spaces (4 for Go files)
  - Line length: 140 characters max
  - Braces: `else`, `catch`, and `finally` on new lines
- Only in `/rustrover` directory you must always use standard Kotlin formatting rules with 4 spaces indentation.
