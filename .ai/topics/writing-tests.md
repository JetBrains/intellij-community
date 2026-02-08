# Writing Tests

Guidelines for writing tests in IntelliJ IDEA codebase.

For examples, see `community/platform/testFramework/junit5/test/showcase/`.

## Prefer JUnit 5 over JUnit 4

Use JUnit 5 with `@TestApplication` annotation instead of extending `LightJavaCodeInsightFixtureTestCase`.

**Why JUnit 5:**
- **Faster**: No class hierarchy overhead, shared fixtures via companion objects
- **Cleaner**: Annotations (`@TestDisposable`, `@RegistryKey`) instead of manual setup/teardown
- **Flexible**: Mix EDT and non-EDT tests in one class, parameterized tests, nested tests
- **Better isolation**: Each test gets fresh disposables automatically

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

Use JUnit 5 lifecycle annotations for setup and teardown:

```kotlin
@TestApplication
internal class MyTest {
  companion object {
    @JvmStatic
    @BeforeAll
    fun setUpClass() {
      // Once before all tests in class
    }

    @JvmStatic
    @AfterAll
    fun tearDownClass() {
      // Once after all tests in class
    }
  }

  @BeforeEach
  fun setUp() {
    // Before each test method
  }

  @AfterEach
  fun tearDown() {
    // After each test method
  }
}
```

**Note:** Prefer `@TestDisposable` over manual `@AfterEach` cleanup for resources.

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

## Running Tests in EDT

Use `@RunInEdt` annotation to run test methods in EDT:

```kotlin
@TestApplication
@RunInEdt
class MyEdtTest {
  @Test
  fun testInEdt() { ... }
}

// Or for specific methods only
@TestApplication
@RunInEdt(allMethods = false)
class MyTest {
  @Test
  @RunMethodInEdt
  fun testInEdt() { ... }
}
```

## Key Classes

- `com.intellij.testFramework.junit5.TestApplication` - initializes shared application
- `com.intellij.testFramework.junit5.TestDisposable` - injects test disposables
- `com.intellij.testFramework.junit5.RegistryKey` - sets registry values
- `com.intellij.testFramework.junit5.SystemProperty` - sets system properties
- `com.intellij.testFramework.junit5.RunInEdt` - runs tests in EDT
- `com.intellij.testFramework.junit5.fixture.projectFixture` - creates project fixtures
- `com.intellij.testFramework.junit5.fixture.moduleFixture` - creates module fixtures

## Showcase Tests

- `JUnit5ProjectFixtureTest.kt` - project fixture patterns
- `JUnit5DisposableTest.kt` - disposable injection
- `JUnit5SystemPropertyTest.kt` - system property usage
- `JUnit5RunInEdtTest.java` - EDT execution

## Running Tests

To run tests via command line, see [TESTING.md](./TESTING.md).

Quick example:
```bash
./tests.cmd -Dintellij.build.test.patterns=*MyTest
```
