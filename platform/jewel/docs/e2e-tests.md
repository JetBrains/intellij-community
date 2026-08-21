# End-to-end tests

Jewel has end-to-end tests that drive the components showcase in both hosts: the standalone sample and the IDE. The
same scenarios run in both, so a behaviour that works in one host and breaks in the other fails the build.

The tests use [Spectre](https://spectre.sebastiano.dev) to find and click Compose nodes. They need a display.

## What runs where

| | Standalone | IDE |
|---|---|---|
| Target | `//platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-e2e-tests` | `//platform/jewel/ide-laf-bridge:jewel-bridge-e2e-tests` |
| Test class | `StandaloneShowcaseE2ETest` | `JewelBridgeShowcaseE2ETest` |
| Host | The showcase views in a Compose window, inside the test JVM | DevKit's "Jewel Components Showcase" dialog, in a dev IDE that the IDE Starter builds from sources |
| Spectre | Runs in the test JVM | Attaches to the IDE process. The IDE gets no Spectre dependency |
| Macro | `standalone_e2e_test` in [standalone-e2e.bzl](../standalone-e2e.bzl) | `bridge_e2e_test` in [bridge-e2e.bzl](../bridge-e2e.bzl) |

The scenarios live in `platform/jewel/showcase-e2e`. That module depends on neither Spectre nor the IntelliJ Platform.
Each host implements the small `ShowcaseAutomator` interface, and the scenarios only talk to that interface.

## Running the tests

```bash
./bazel.cmd test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-e2e-tests
./bazel.cmd test //platform/jewel/ide-laf-bridge:jewel-bridge-e2e-tests
```

The first run of the IDE test is slow, because it builds the whole community repository. Later runs take about one
minute.

| OS | What you need |
|---|---|
| macOS | Nothing extra. Windows open on your desktop while the tests run |
| Linux | A display. `xvfb-run` works. Both tests read `DISPLAY` and `XAUTHORITY` from your shell |
| Windows | `--enable_runfiles`, and an interactive desktop that stays unlocked. A service session or a closed remote desktop session does not work |

Do not use the keyboard while the IDE test runs. The IDE test presses keys through the operating system, so the IDE
window must keep the keyboard focus.

## Why the IDE test presses keys through the IDE Driver

When Spectre is attached to another process, it sends synthetic AWT events straight to the Compose component. These
events do not go through `IdeEventQueue`. An open `JBPopup` handles keys in that queue, so a key sent by Spectre never
reaches it. The IDE test therefore presses keys with the IDE Driver's robot, which sends real keyboard input. Clicks
still go through Spectre.

Before it presses any key, the test checks that the IDE receives keyboard input from the operating system. If this
check fails, the message is `the IDE to receive OS keyboard input`.

## Adding scenarios

A scenario is a suspending function that takes a `ShowcaseAutomator`. It returns `null` when the behaviour is correct,
or a sentence that says what went wrong.

1. Give the component a test tag in the showcase, and add the same string to `ShowcaseE2eTags`.
2. Add the scenario to an existing group such as `PopupScenarios`, or create a new object for a new area of the
   showcase.
3. Give the group a `runAll` function. It opens the right showcase view and calls `runIsolated` for each scenario.
   `runIsolated` takes a cleanup function, which brings the UI back to a known state after each scenario.
4. Call the group's `runAll` from both test classes.

If a scenario needs an action that `ShowcaseAutomator` does not have, add it to the interface and implement it in
`SpectreShowcaseAutomator` and `AttachedShowcaseAutomator`.

## CI

Both tests carry the `requires-display` tag. CI selects them by that tag, so a new test target joins CI without a
list to update:

```bash
bazel test //platform/jewel/... --build_tests_only --test_tag_filters=requires-display
```

GitHub Actions runs four jobs from [jewel-checks.yml](../../../.github/workflows/jewel-checks.yml), on Linux under
Xvfb and on Windows: `Jewel Spectre tests` for the standalone tests, and `Jewel IDE end-to-end tests` for the IDE
test, selected by its `jewel-ide-e2e` tag. The IDE test has its own jobs because its build compiles the whole platform,
and the standalone tests time out when they run during that build.

TeamCity is configured in a JetBrains repository that Jewel contributors outside JetBrains cannot see. Ask a JetBrains
maintainer to confirm that a display-capable agent runs the command above. An agent without a display must exclude
the tests with `--test_tag_filters=-requires-display`.

## When a test fails

| Message | Likely cause |
|---|---|
| `the IDE to receive OS keyboard input` | Another window has the keyboard focus, or the session has no input desktop. On Windows, check for a Windows Defender Firewall prompt in front of the IDE. It appears the first time a new JBR opens a port |
| `Timed out ... Visible tags: [...]` | The tag is wrong, or the view did not open. The list shows what the automator can see |
| `ComposeNotOnClasspathException` | Spectre attached before the showcase dialog opened |
| Black screenshots, pixel checks with `diff=0` | The desktop session is locked or disconnected |
