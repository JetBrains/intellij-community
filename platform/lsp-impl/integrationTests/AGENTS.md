# LSP Client integration tests

These tests start a real IDE and drive it with the Driver SDK. The LSP client, the editor and the background highlighting are the ones a user gets. For the tests that run in unit test mode, see `../tests/AGENTS.md`.

## Layout

- `testSrc/.../fakeScenario/`: the scenario tests, with the fake server. Each kind of server gets a directory of its own.
- `testSrc/.../fakeServer/`: the fake server and the scenario API (`FakeLspIntegration.scenario { }`).
- `testSrc/.../util/`: the shared IDE (`SharedLspIde`), the editor helpers, and the choice of the product (`LspIntegrationTestEnvironment`).
- `idePlugin/`: the test-only plugin that the tests add to the IDE. It has the LSP integration that starts the fake server, the test language, and `LspIntegrationTestService`, which a test calls through a `@Remote` interface.
- The ultimate checkout has `platform/lsp-impl/integrationTests`, which runs the same tests in JetBrains Light and in Docker.

## How a scenario test reaches the server

- The IDE starts a real server process. That process is `FakeLspServerRelay`, which only copies bytes between its stdio and a socket of the test process.
- The fake server itself is in the test process, so expectations are plain lambdas, as in the unit tests.
- A message that no expectation matches fails the test, and so does an expectation that never matches.

## Writing a test

- Type with `editor.typeAt` or `editor.typeOver`. They use a shorter delay between the characters than `keyboard { typeText() }`.
- Use as few input devices as possible, and invoke IDE actions. For example, `editor.putCaretAt` followed by `invokeAction("QuickJavaDoc")` is preferred to `editor.hoverOnPosition`, although a hover should show the same documentation. Use the mouse only when the mouse is what the test is about, such as a click with the modifier key on a link.
- Start a test class with `Lsp`, then the kind of server, then the feature: `LspFakeScenarioDiagnosticsTest`.
- Take `FakeLspIntegration` as a test parameter and put the test body in `fakeLsp.scenario { }`. The class needs `@ExtendWith(SharedLspIde::class)`.
- All classes share one IDE and one open project. Before each test the editors close and the servers stop. Nothing restores files, so create the files of a test with `addFile`: each scenario gets a directory of its own.
- The test body is blocking. Handlers of expectations are suspend lambdas. Wait for a `Deferred` with `awaitBlocking`.
- The client sends some requests right after `didOpen`. Expect them on `unstartedServerSession()` before the file opens.
- Check what the editor shows: `waitForHighlighting` for errors and warnings, `waitForTextAttributes` for other highlighters.
- Pass `waitForCodeAnalysis = false` when a file is opened through the Driver SDK directly. JetBrains Light stays in dumb mode, so that wait never ends there.
- A lambda of the test can't configure the client, because the client runs in the IDE process. A client-side customization needs a named entry in `FakeLspClientCustomization` of the test plugin, and its name in `ClientCustomization`.
- Calls into the test plugin pass strings and primitives only.

## Differences from the unit tests

- A typed Enter is not `insertString("\n")`: the editor indents the new line.
- A zero-length diagnostic inside a line is one character wide in the real editor.
- Each typed character can cause a diagnostic pull, and how many go out depends on timing. A test that types allows unmatched `textDocument/diagnostic`.
- `textDocument/documentHighlight` is sent for the caret position when a file opens, and for the click that gives the editor the focus.

## The test plugin

- Its `plugin.xml` is written by hand. No product bundles the plugin and no generator knows it.
- The module is a test module. The dev build of the IDE under test packs it only with the system property `idea.build.pack.test.source.enabled`, which `LspTestProject.kt` sets in the test process.

## Test groups

- The tests are in the test group `LSP_CLIENT_INTEGRATION_TESTS` of `testResources/tests/testGroups.properties`. A test class outside every group fails `UltimateProjectTestsStructureTest`, so a new directory with tests needs a line in that file.
- The Community aggregator doesn't run these tests: the module has no `community-aggregator` tag, and `intellij.idea.community.main.tests` doesn't depend on it. Keep it that way, because the aggregator allows no external processes.

## Running

The tests run with `tests.cmd`. They need an IDE built from sources, which the first run builds. The dependency on `intellij.tools.ide.starter.build.server` is what makes Starter build the IDE. Without it Starter downloads a released IDE, which doesn't have the test plugin.

IDEA Community, on this machine. The IDE takes the screen, the keyboard and the mouse while the tests run:

```
./tests.cmd --module intellij.platform.lsp.impl.integrationTests \
  --test 'com.intellij.platform.lsp.integrationTests.fakeScenario.*'
```

In the ultimate checkout, JetBrains Light in a Docker container. Nothing shows up on the screen, and the IDE is visible at http://127.0.0.1:6080/vnc.html:

```
USE_DOCKER_CONTAINER=true ./tests.cmd --module intellij.platform.lsp.impl.integrationTests.ultimate \
  --test com.intellij.platform.lsp.integrationTests.ultimate.LspIntegrationUltimateTestSuite
```

- The suite class selects the tests by package. A new directory for another kind of server needs an entry in `LspIntegrationUltimateTestSuite`.
- JetBrains Light runs with `ijlight.workspace.memberProjects=false`. With member projects on, two projects are open in one frame, and the Driver SDK calls that look for the single open project fail.
- Add `-Dpass.lsp.integrationTests.ide=idea-community` to run IDEA Community in the container.
- Without `USE_DOCKER_CONTAINER` the same command runs JetBrains Light on this machine.
- The logs of a run are in `out/ide-tests/tests/<IDE>/<class in kebab case>/<test>/log/`: `idea.log`, screenshots and a screen recording.
- A killed run can leave its container behind. Stop the `ide-image-for-tests` container before the next run.
