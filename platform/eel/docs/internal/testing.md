# Testing Eel

## Test Modules

| Module | Directory | Content |
| --- | --- | --- |
| `intellij.platform.eel.tests` | `community/platform/eel/tests/` | Unit tests for the API module: paths, channels, class loaders, `SafeDeferred`. |
| `intellij.platform.eel.testFramework` | `community/platform/eel/testFramework/src/` | Coroutine test helpers for Eel tests. |
| `intellij.platform.eel.codegen` | `community/platform/eel/codegen/` | `BuildersGeneratorTest`. It regenerates the builders in `gen-builders/`. See [Module Layout](module-layout.md). |
| `intellij.platform.testFramework.junit5.eel` | `community/platform/testFramework/junit5/eel/src/` | The framework for Eel API users. The deprecated `eelFixture()` and `IsolatedFileSystem` also live here. |
| `intellij.platform.testFramework.junit5.eel.tests` | `community/platform/testFramework/junit5/eel/test/` | `@TestApplicationWithEel` and `@EelSource` in `params/api/`, and their tests. |
| `intellij.platform.ijent.testFramework` | `platform/ijent/testFramework/src/` (ultimate) | `EelFixture`, `eelTestFactory`, the fixture factories, and the remote Eel providers for `@TestApplicationWithEel`. |

## Run a Test

```bash
./tests.cmd --module intellij.platform.eel.tests --test 'com.intellij.platform.eel.path.EelAbsolutePathTest'
```

The `--test` argument takes a fully qualified name or a wildcard. A simple class name matches nothing. `tests.cmd` compiles with Bazel, so a separate build step is not needed.

## Two Test Frameworks

There are two frameworks for tests that need an Eel. Pick the one that matches your goal.

| Framework | Audience | Module | Shape |
| --- | --- | --- | --- |
| `@TestApplicationWithEel` | Eel API users. The code under test calls Eel. | `intellij.platform.testFramework.junit5.eel` (community) | A class annotation and an `EelHolder` parameter. |
| `EelFixture` | Eel and IJent developers. The code under test is Eel or IJent itself. | `intellij.platform.ijent.testFramework` (ultimate) | A JUnit 5 `@TestFactory` that returns `eelFixtures.eelTestFactory { }`. |

Both frameworks read `EelFixtureFilter` from the system property `eel.test.fixtures`. The labels are `local`, `local-eel`, `local-ijent`, `docker`, and `wsl`. An empty value enables all of them. The annotations `EelFixtureFilter.OnlyWhenDockerEnabled`, `OnlyWhenWslEnabled`, `OnlyWhenLocalEelEnabled`, and `OnlyWhenLocalIjentEnabled` skip a single test or class.

Do not use `eelFixture()` and `IsolatedFileSystem` from `intellij.platform.testFramework.junit5.eel`. They are deprecated, and they use mocks. They are not related to `EelFixture`.

### `@TestApplicationWithEel`: Test Code That Uses Eel

Mark the test class with `@TestApplicationWithEel` and take an `EelHolder` parameter. The framework runs the test against the local Eel and against at least one remote, IJent-based Eel. Eel and IJent are not under stress in such a test. Read the KDoc on `com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel` for the details. Two points from that KDoc:

- The test needs the VM option `-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider`. A failed run reports the option name.
- The remote providers come from `intellij.platform.ijent.testFramework`. Put it on the test classpath.

### `EelFixture`: Test Eel and IJent Themselves

`EelFixture` is the internal framework. It is flexible and it tests Eel and IJent at a low level. An Eel API user may use it, but it is overkill for a test that only calls Eel.

The sources live in `platform/ijent/testFramework/src/functional/fixture/`. The pieces:

- `EelFixture` is a `CoroutineScope` with `eelMachine`, `eelApi`, and `startProcess`. `EelPosixFixture` and `EelWindowsFixture` narrow `eelApi` to `EelPosixApi` or `EelWindowsApi`. `IjentFixture` adds IJent-specific members.
- `EelFixture.Factory` creates one fixture in a `ParentOfIjentScopes`. The factory sequences in `ijentFixtures.kt` select the environments: `eelFixtures`, `eelPosixFixtures`, `eelWindowsFixtures`, `eelLocalFixtures`, `ijentFixtures`, `ijentLocalFixtures`, `ijentWslSaneFixtures`, `ijentDockerFixtures`. The local IJent fixtures start IJent on the developer machine over stdio and over TCP.
- `eelTestFactory` in `ijentFixtureTestFactory.kt` turns a sequence of factories and a test body into a list of JUnit 5 `DynamicNode`. The test body is a `suspend EelFixture.() -> Unit`. `eelMultiTestFactory` runs several named bodies against every fixture.

A test looks like this:

```kotlin
@TestApplication
class MyEelTest {
  @TestFactory
  fun `read a file`() = eelFixtures.eelTestFactory {
    val dir = eelApi.fs.createTemporaryDirectory().deleteOnExit(true).getOrThrow()
    // Work with eelApi, eelMachine, or startProcess.
  }
}
```

Facts about a run, taken from `runTest` in `ijentFixtureTestFactory.kt`:

- The fixture must start in one minute. A run that fails this bound logs a thread dump and fails.
- Each test body has a timeout. The default is `eachTestDefaultTimeout`, 600 s. Pass `eachTestTimeout` to change it. With a debugger attached outside TeamCity, the timeout becomes infinite.
- `canCloseIjentDuringTest = false` (the default) registers the fixture in `EelFixtureOverridingService`, so the platform code sees the test Eel. With `true`, the test may call `IjentApi.close` and continue.
- If every fixture is filtered out, the factory throws an `AssumptionViolatedException`, so the test is skipped.
- On TeamCity the factory flattens the dynamic test tree, so the report shows one line per fixture.

Integration tests that use `EelFixture` live in `intellij.platform.ijent.integrationTests`. See `EelExecuteProcessTest` and `EelFileSystemTest2` in `platform/ijent/integrationTests/testSrc/` for larger examples. Run them with `./tests.cmd --module intellij.platform.ijent.integrationTests --test <FQN or wildcard>`.

## Regenerate Builders

Run `BuildersGeneratorTest` from `intellij.platform.eel.codegen` after you change a method with a `@GeneratedBuilder` argument. Commit the new files in `community/platform/eel/gen-builders/`.
