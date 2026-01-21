# Driver UI Tests Guide

Guidelines for writing UI tests using IDE Starter and UI Driver frameworks.

## Common Imports

```kotlin
// Driver core
import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.waitForProjectOpen
import com.intellij.driver.sdk.advancedSettings

// Test utilities
import com.intellij.driver.tests.utils.waitForIndicators
import com.intellij.driver.tests.utils.Plugin
import com.intellij.driver.tests.utils.PluginInstaller
import com.intellij.driver.tests.utils.Plugins

// IDE Starter framework
import com.intellij.ide.starter.driver.runIdeTest
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.models.IDEStartResult
import com.intellij.ide.starter.models.VMOptions
import com.intellij.ide.starter.runner.IDERunContext
import com.intellij.ide.starter.runner.Starter
import com.intellij.ide.starter.utils.catchAll

// Extended test infrastructure
import com.intellij.ide.starter.extended.allure.AllureHelperExtended.step
import com.intellij.ide.starter.extended.allure.Subsystems
import com.intellij.ide.starter.extended.engine.newTestContainerExtended
import com.intellij.ide.starter.extended.engine.TestContainerExtended
import com.intellij.ide.starter.extended.license.StagingLicenseGenerator.licenseProductCode
import com.intellij.ide.starter.extended.loadMetadataFromServer
import com.intellij.ide.starter.extended.setupTestMetadataSchemeWithGroupsFromCode

// Test framework
import com.intellij.testFramework.TestApplicationManager
```

## Test Structure
- Tests use JUnit 5 with an IDE Starter framework and UI Driver framework (`community/platform/remote-driver`)
- Test case projects are represented by the `com.intellij.ide.starter.models.TestCase` see `src/com/intellij/ide/starter/project`
- Tests run against specific IDE (`community/tools/intellij.tools.ide.starter/src/com/intellij/ide/starter/ide/IdeProductProvider.kt`)

## Test project examples

See `tests/intellij.ide.starter.extended/src/com/intellij/ide/starter/extended/data/cases`

## Page Object Pattern or UiComponent

Page objects extend `UiComponent` with `ComponentData` constructor:

```kotlin
class MyPageObject(data: ComponentData) : UiComponent(data) {
    val myButton = x { byAccessibleName("Button Name") }
    val myPanel = x { byClass("PanelClassName") }

    fun clickMyButton() {
        myButton.click()
    }
}

// Extension function on Finder to create the page object
fun Finder.myPageObject(): MyPageObject = x(
    xQuery { byAccessibleName("Root Element Name") }, MyPageObject::class.java
)

// Use specific ui component to specify the context of the search
fun AnotherPageObject.myPageObject(): MyPageObject = x(
  xQuery { byAccessibleName("Root Element Name") }, MyPageObject::class.java
)
```

## Element Selectors

| Selector | Usage |
|----------|-------|
| `byAccessibleName("name")` | Find by accessible name attribute |
| `byClass("ClassName")` | Find by Swing/AWT class name |
| `byVisibleText("text")` | Find by visible text content |


## UI test examples

See directory `tests/remote-driver-tests`


## Common UI Components

See directory `community/platform/remote-driver/test-sdk/src/com/intellij/driver/sdk/ui`

## Scoping Element Searches

When multiple elements match a selector, scope to a parent element:

```kotlin
// BAD - will fail if multiple InstallButtons exist
ui.x { byClass("InstallButton") }.click()

// GOOD - scope to a parent element first
val detailPane = ui.x { byClass("PluginDetailsPageComponent") }
detailPane.x { byClass("InstallButton") }.click()
```

## Keyboard Interactions

```kotlin
element.keyboard { typeText("search text") }
ui.keyboard { key(KeyEvent.VK_ENTER) }
ui.keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA) }  // Cmd+,
```

## Writing Tests

### Basic Test Structure

```kotlin
class MyTest {
    val testCase = TestCase(IdeProductProvider.IU, myProject)

    @Test
    fun `my test name`(testInfo: TestInfo) {
        val context = Starter.newContext(testName = "TestName", testCase = testCase)

        context.applyVMOptionsPatch {
            addSystemProperty("ide.ui.non.modal.settings.window", "true")
        }

        context.runIdeTest(testName = testInfo.displayName) {
            waitForIndicators(5.minutes)  // Wait for indexing to complete
            
            step("Step description") {
                // Test actions here
            }
        }
    }
}
```

### Waiting for Project Import and Indexing

Always wait for indicators at the start of your test:

```kotlin
waitForIndicators()
```

This ensures the project is fully imported and indexed before interacting with the IDE.

### Opening Files

Use `openFile` instead of UI-based file navigation:

```kotlin
// GOOD - Direct and reliable
openFile(relativePath = "src/Main.java")

// AVOID - UI-based approach is slower and more fragile
invokeAction("GotoFile", now = false)
ui.keyboard { typeText("Main.java") }
ui.keyboard { key(KeyEvent.VK_ENTER) }
```

### invokeAction: `now` Parameter

The `now` parameter controls whether the action completes before continuing:

```kotlin
// now = true: Waits for action to complete (use when keyboard input follows)
invokeAction("ToggleBookmarkWithMnemonic", now = true)
ui.keyboard { key(KeyEvent.VK_1) }  // This input goes to the bookmark dialog

// now = false: Returns immediately (use when waiting for UI to appear)
invokeAction("ShowSettings", now = false)
ui.x { byClass("SettingsDialog") }.shouldBe { present() }
```

**Rule**: Use `now = true` when the next step is keyboard input to prevent input going to the wrong component.
**Rule**: Use `now = false` when you expect to the UI dialog to appear.

### Custom Wait Conditions

Use `waitFor` to wait for specific conditions:

```kotlin
waitFor("description of what we're waiting for", 30.seconds) {
    ui.x { byClass("MyComponent") }.present()
}

waitFor("text to appear", 10.seconds) {
    ui.x { byClass("Tree") }.hasText("expected text")
}
```

## Running Tests from Terminal

Driver tests require a fully built IDE. There are several ways to run them:

### Option 1: Using tests.cmd (Recommended)

The `tests.cmd` script builds the IDE from sources and runs tests. **Recommended for dev server mode.**

Example:
```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.intellij.driver.tests.idea.java.FindAndGoToTest \
  -Dintellij.build.test.main.module=intellij.driver.tests
```

**Key parameters:**
- `-Dintellij.build.test.patterns` - fully qualified test class name (or pattern)
- `-Dintellij.build.test.main.module=intellij.driver.tests` - **required** for driver tests

**Example with specific test:**
```bash
./tests.cmd \
  -Dintellij.build.test.patterns=com.intellij.driver.tests.idea.ultimate.httpclient.BuiltInHttpClientBrotliCompressionUiTest \
  -Dintellij.build.test.main.module=intellij.driver.tests
```

## Debugging Test Failures

### Output Locations

After test failure, check:
- UI hierarchy: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/ui-hierarchy/ui.html`
- IDE log: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/idea.log`
- Screenshots: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/log/screenshots/`
- Exceptions: `out/ide-tests/tests/{IDE-version}/{TestName}/{test-method}/error/`

### Common Issues

1. **Element Not Found**: Check UI hierarchy HTML for the correct accessible name or class
2. **Multiple Elements Match**: Scope search to parent element


## Critical Rules

- **Never use `Thread.sleep()`** or `delay()` - Driver framework automatically waits for UI elements
- **Wrap test logic in `step("description") { }`** for better logs
- **Verify assertions actually fail** – Comment out the action being tested and confirm the test fails. If it still passes, your assertion is too weak.
- **Use common UI components** – create new if necessary
- **Always check UI hierarchy** to understand the UI state when a test fails
