---
name: driver-ui-tests
description: Write IntelliJ UI tests with IDE Starter or UI Driver.
---

# Driver UI Tests Guide

Guidelines for writing UI tests using IDE Starter and UI Driver frameworks.

Three files sit next to this one. Read [find-elements.md](find-elements.md) when you look for a component or read
the IDE state. Read [api.md](api.md) when you need the call for an action, a wait or a startup flag. Read
[debug.md](debug.md) when a test fails.

## Critical Rules

These hold for every Driver test:

- **Never use `Thread.sleep()`** or `delay()` - Driver framework automatically waits for UI elements
- **Pass `pauseOnIndicators` instead of a manual `waitForIndicators`** - see "Test Structure" below
- **Write one call chain** from the context to `useDriverAndCloseIde` - see "Test Structure" below
- **Wrap test logic in `step("description") { }`** for better logs
- **Verify assertions actually fail** – Comment out the action being tested and confirm the test fails. If it still passes, your assertion is too weak.
- **Use common UI components** – create new if necessary
- **Keep a helper for a specific test scenario `private`** – keep it in its test class. Put a reusable helper into a common UI component
- **Do not add retries to test-sdk** – a retry hides the cause of a failure. Find the cause and wait for the correct UI state. If a test really needs `withRetries`, keep it in the test module
- **Do not write a workaround for a bug** – when the cause is a defect of the product, of the test framework or of the Driver API, file an issue in the `AT` project of YouTrack, and link it from the test. Use the `youtrack` skill. A workaround, such as a sleep, a second attempt, a `try`/`catch` or a weaker assertion, hides the defect and keeps the test green over broken behavior

## Where to look

A test uses JUnit 5 with the IDE Starter framework and the UI Driver framework:

| What you need | Where it is |
|---|---|
| The Driver framework | `community/platform/remote-driver` |
| Common UI components | `community/platform/remote-driver/test-sdk/src/com/intellij/driver/sdk/ui` |
| UI test examples | `tests/remote-driver-tests` |
| Runnable examples of each rule here | `tests/intellij.ide.starter.extended/tests/com/intellij/ide/starter/extended/docexamples` |
| The test case of a project, a `com.intellij.ide.starter.models.TestCase` | a `*Cases.kt` or a `*CachedProjects.kt` of the product, such as `tests/intellij.ide.starter.extended.idea.ultimate/src/com/intellij/ide/starter/extended/idea/ultimate/CachedProjects.kt` |
| The IDE a test runs against | `community/tools/intellij.tools.ide.starter/src/com/intellij/ide/starter/ide/IdeProductProvider.kt` |
| The annotations of a test | `tests/intellij.ide.starter.allure.annotations/src/com/intellij/ide/starter/extended/allure/Annotations.kt` |

## Required Annotations

Every UI test **must** have the following annotations at the class level:

| Annotation | Purpose | TestOps Custom Field |
|------------|---------|---------------------|
| `@Subsystems.*` | Categorizes the test by subsystem | `Subsystem` |
| `@Features.*` | Specifies the feature being tested | `Feature` |
| `@Components.*` | Identifies the component under test | `Component` |

**For tests linked to TestOps test cases**, also add `@AllureId("test_case_id")` on the test method. The annotation
values should match the corresponding TestOps custom fields (Subsystem, Feature, Component).

## Test Structure

Write one call chain from the context to `useDriverAndCloseIde`. Do not keep the context in a variable, and do not
keep a `BackgroundRun` or a `Driver` in a field. A kept context invites a second launch over project files that no
call rewound. A local `val run = ...runIdeWithDriver()` is correct when the test calls `run.restart()`, because
`restart()` needs that run.

`pauseOnIndicators` waits for the indicators before each Driver call, so it also prevents a failure in the middle
of the test. Pass it to `runIdeWithDriver`, and write no `waitForIndicators` in the test.

```kotlin
@Subsystems.Java
@Features.Completion
@Components.Editor
class MyTest {

    @Test
    @AllureId("123456")  // Required if test case exists in TestOps
    fun `my test name`() {
        Starter.newContext(ideInfo = IdeInfo.IdeaUltimate) { project = myProject }
            .applyVMOptionsPatch {
                addSystemProperty("ide.ui.non.modal.settings.window", "true")
            }
            .runIdeWithDriver(pauseOnIndicators = 5.minutes)
            .useDriverAndCloseIde {
                ideFrame {
                    openFile("src/Main.java")

                    step("Step description") {
                        // Test actions here
                    }
                }
            }
    }
}
```

`applyVMOptionsPatch` is safe here because the context is a fresh one for this test. Never patch a cached or a
shared context: the change would reach every test that reuses it.

Two cases need more than the pause:

- **The first call does not wait for a frame.** The pause waits only on a project that is already open. A UI search
  waits up to 15 s for its component, and `openFile` waits for the project frame, but an action that opens a modal
  dialog, a call such as `singleProject()`, and a key press all run at once. Put `waitForProjectOpen(Timeouts.M)`
  before such a call, and after the test opens or reopens a project.
- **The pause does not work for the test.** Only then call `waitForIndicators`, and mark the test with
  `@OptIn(ManualWaitForIndicators::class)`. An inspection warns about each other manual wait.

## Running Tests from Terminal

Driver tests require a fully built IDE. The `tests.cmd` script builds the IDE from sources and runs the tests.
Run each local test with `USE_DOCKER_CONTAINER=true`. The container gives a screen of its own, so the test does not
lose the focus to your desktop, and the environment matches the build agent:

```bash
USE_DOCKER_CONTAINER=true ./tests.cmd \
  --module intellij.driver.tests \
  --test com.intellij.driver.tests.idea.java.FindAndGoToTest
```

**Key parameters:**
- `--test` - fully qualified test class name (or pattern)
- `--module intellij.driver.tests` - **required** for driver tests

After the run in the container passes, run the test once without the flag. Some failures come up in one
environment only.
