# CI Stability Gate

Prevent regressions by failing the build when a previously-skippable composable becomes
non-skippable, or when a stable class becomes unstable.

## Option A — compose-stability-analyzer (single-module)

A Gradle plugin that dumps stability to JSON and provides a `stabilityCheck` task.

### Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.google.android.libraries.compose.stability.analyzer") version "1.0.0"
}
```

### Workflow

1. Run `./gradlew stabilityDump` to generate a baseline JSON.
2. Commit the baseline to `src/main/resources/stability-baseline.json`.
3. In CI, run `./gradlew stabilityCheck`. The task fails if any composable or class regresses
   compared to the baseline.

## Option B — ComposeGuard (multiplatform)

A community Gradle plugin with broader Kotlin Multiplatform support.

### Setup

```kotlin
// build.gradle.kts
plugins {
    id("com.joetr.compose.guard") version "0.5.0"
}

composeGuard {
    check = true
    outputDirectory = layout.buildDirectory.dir("compose-guard")
}
```

### Workflow

1. Run `./gradlew composeGuardGenerate` to create the baseline.
2. Commit the generated files.
3. CI runs `./gradlew composeGuardCheck`.

## What to gate

| Gate | Severity | Rationale |
|------|----------|-----------|
| Non-skippable composable that was previously skippable | **Error** | Direct regression; recomposition cost added. |
| Stable class that becomes unstable | **Error** | May cascade into many composables. |
| New non-skippable composable | **Warning** | Not necessarily a regression if the composable is cold-path. |
| Skippability count drop in `module.json` | **Warning** | Trend indicator; investigate but do not block. |

## Desktop-specific notes

- Desktop modules do not use AGP, so Option A may need manual plugin application.
- Option B (ComposeGuard) is more likely to work out of the box with Kotlin/JVM modules.
- Baselines should be checked into version control and updated intentionally, not auto-regenerated
  in CI.
