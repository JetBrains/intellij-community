---
name: writing-tests
description: Write IntelliJ JUnit 5 tests with fixtures, lifecycle, and EDT.
---

# Writing Tests

For examples, see `community/platform/testFramework/junit5/test/showcase/`.

## Place Tests in the Owning Module

Put a test in the test module associated with the production module it exercises. Do not place it in a downstream module merely because
that module has the production module on its test classpath. Check the production module's `.iml` file and neighboring tests before adding
a new test.

A test of `org.jetbrains.intellij.build.io` code under `community/build/tasks` belongs in `intellij.idea.community.build.tasks.tests`
(`community/build/tasks/test`), not in `intellij.platform.buildScripts.tests`. There, such a test is outside every community test group,
and `UltimateProjectTestsStructureTest` fails. The `BUILD_SCRIPTS_PLATFORM_TESTS` group deliberately excludes
`org.jetbrains.intellij.build.io.*`.

## Prefer JUnit 5 over JUnit 4

Use JUnit 5 with `@TestApplication` annotation instead of extending `LightJavaCodeInsightFixtureTestCase`.

## Shared Fixtures Pattern

Use companion object fixtures shared between all tests:

```kotlin
@TestApplication
internal class MyTest {
  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture("src")
  }

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()
}
```

## Lifecycle Hooks

Prefer `@TestDisposable` over manual `@AfterEach` cleanup for resources.

## Test Disposables

Use `@TestDisposable` annotation to inject test-scoped disposables (created before each test, disposed after):

```kotlin
@TestDisposable
lateinit var disposable: Disposable

// Or as parameter
@Test
fun myTest(@TestDisposable disposable: Disposable) { ... }
```

## Registry Values in Tests

Use `@RegistryKey` annotation instead of `Registry.get().setValue()`:

```kotlin
@Test
@RegistryKey(key = "my.registry.key", value = "true")
fun testWithRegistryEnabled() { ... }
```

## System Properties in Tests

Use `@SystemProperty` annotation instead of `System.setProperty()`:

```kotlin
@Test
@SystemProperty(propertyKey = "my.property", propertyValue = "value")
fun testWithSystemProperty() { ... }
```

## Coroutines and UI Tests

Use `com.intellij.testFramework.common.timeoutRunBlocking` as the coroutine boundary. It has a 10-second default timeout, so a
suspended test fails quickly with a coroutine-aware thread dump. Do not add a JUnit `@Timeout` to such a test. The two timeouts
duplicate each other, and the `@Timeout` gives a worse diagnostic.

Keep setup, background work, and assertions off the UI thread. Move only Swing operations into a small
`withContext(Dispatchers.UI)` block:

```kotlin
@Test
fun updatesLabel(): Unit = timeoutRunBlocking {
  val value = loadValue()
  val actual = withContext(Dispatchers.UI) {
    label.text = value
    label.text
  }
  assertThat(actual).isEqualTo(value)
}
```

`Dispatchers.UI` is strict: it supplies UI-thread affinity without implicit model access. Use `Dispatchers.EDT` only when the tested
operation genuinely requires model or lock access and cannot be split from the UI operation. Keep write actions explicit.

Editor creation/disposal, editor document mutation, completion invocation, action-group expansion, and file-editor operations are common
model-backed exceptions. Keep their full synchronous lifecycle in a narrow `Dispatchers.EDT` block; do not construct on strict UI and
dispose later from a different dispatcher.

Do not use `@RunInEdt`, `@RunMethodInEdt`, `runInEdtAndWait`, or `runInEdtAndGet` in new tests. They hide the coroutine boundary,
move lifecycle methods and assertions onto EDT, and can accidentally grant model or write-intent access.

When a synchronous callback API cannot call a suspending function, use a narrow bounded adapter around that callback only:

```kotlin
timeoutRunBlocking(timeout = 10.seconds, context = Dispatchers.UI) {
  createSwingComponent()
}
```

Prefer observable completion signals, flows, latches, or virtual time over sleeps. If polling is unavoidable, bound it and make the
predicate suspending so UI checks can use `withContext(Dispatchers.UI)` without nested blocking.

Use the enclosing test scope for launched work: pass `this` from `timeoutRunBlocking`/`coroutineScope`, or `backgroundScope` from
`runTest`, into the code under test. Create a standalone `CoroutineScope(...)` only when independent scope lifetime is itself under test;
parent it to the test job and cancel it in `finally`. Use `delay` freely with `runTest` virtual time, but do not use a real timing-only
delay as a completion signal.

## Key Classes

- `com.intellij.testFramework.junit5`: `TestApplication` (initializes the shared application), `TestDisposable`, `RegistryKey`,
  `SystemProperty`
- `com.intellij.testFramework.junit5.fixture`: `projectFixture`, `moduleFixture`

## Showcase Tests

- `JUnit5ProjectFixtureTest.kt` - project fixture patterns
- `JUnit5DisposableTest.kt` - disposable injection
- `JUnit5SystemPropertyTest.kt` - system property usage
- `JUnit5RunInEdtTest.java` - legacy EDT extension behavior; do not copy it for new tests

## Running Tests

To run tests via command line, see the [testing](../testing/SKILL.md) skill.
