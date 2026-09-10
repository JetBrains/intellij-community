# stability_config.conf Syntax

The Compose Compiler accepts a plain-text configuration file that overrides stability verdicts for
classes the compiler cannot inspect (Java classes, third-party Kotlin, separately compiled
modules).

## File location

Place the file anywhere in the repo. Convention:

```
project-root/
├── stability_config.conf          # project root
└── feature-module/
    └── stability_config.conf      # module-local override
```

## Gradle wiring

```kotlin
composeCompiler {
    stabilityConfigurationFile = rootProject.file("stability_config.conf")
}
```

## Syntax

```
// A stable class
com.example.MyModel

// A stable class with generic parameters
com.example.GenericModel

// Comments start with //
// com.example.IgnoredModel
```

Rules:
- One fully-qualified class name per line.
- Blank lines are ignored.
- Comments start with `//`.
- No wildcard or package-level rules. Each class must be listed individually.
- The class does **not** need to exist at compile time. If the class is missing, the entry is
  silently ignored (this is a footgun — verify spellings).

## When to use

| Scenario | Action |
|----------|--------|
| Third-party Java DTO with only `val`-equivalent getters | Add to config. |
| Third-party Kotlin `data class` from a non-Compose module | Add to config if it has no `$stable` field. |
| Your own module's class | Restructure or annotate; do not use config. |
| Android `Parcelable`, `Serializable` | Not relevant on Desktop. Skip. |

## Verification

After adding an entry, rebuild and check `classes.txt`. The class should appear as `Stable` or
`Runtime` instead of `Unstable`.
