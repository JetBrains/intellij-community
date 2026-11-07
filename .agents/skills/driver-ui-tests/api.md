# The API Of A Driver Test

Read this file when you need the call for an action, a wait or a startup flag.

## Opening Files

Use `openFile` instead of UI-based file navigation:

```kotlin
// GOOD - Direct and reliable
openFile(relativePath = "src/Main.java")

// AVOID - UI-based approach is slower and more fragile
invokeAction("GotoFile", now = false)
ui.keyboard { typeText("Main.java") }
ui.keyboard { key(KeyEvent.VK_ENTER) }
```

## invokeAction: `now` Parameter

The `now` parameter controls whether the action completes before continuing:

```kotlin
// now = true: Waits for action to complete (use when keyboard input follows)
invokeAction("ToggleBookmarkWithMnemonic", now = true)
ui.keyboard { key(KeyEvent.VK_1) }  // This input goes to the bookmark dialog

// now = false: Returns immediately (use when waiting for UI to appear)
invokeAction("ShowSettings", now = false)
ui.x { byJavaClass("com.intellij.openapi.options.newEditor.SettingsEditor") }.shouldBe { present() }
```

**Rule**: Use `now = true` when the next step is keyboard input to prevent input going to the wrong component.
**Rule**: Use `now = false` when you expect to the UI dialog to appear.

## Custom Wait Conditions

Use `waitFor` to wait for specific conditions:

```kotlin
waitFor("description of what we're waiting for", 30.seconds) {
    ui.x { byJavaClass("com.example.MyComponent") }.present()
}
```

To show the actual value on failure, use the `waitFor` overload with `getter` and `checker`. It adds the last value to the error, so do not build `errorMessage` yourself:

```kotlin
// BAD - errorMessage repeats the getter
waitFor("the filter field holds '$query'", errorMessage = { "the filter field holds '${filterField.text}'" }) {
    filterField.text == query
}

// GOOD - the error shows the last filterField.text
waitFor("the filter field holds '$query'", getter = { filterField.text }, checker = { it == query })
```

## Enabling a registry flag at startup

Seed a registry key before the IDE starts with a `-D` VM option — `RegistryValue` falls back to `System.getProperty`. Required when a startup `ProjectActivity` or `ToolWindowFactory.shouldBeAvailable` reads the flag (setting it via the driver after start is too late):

```kotlin
.applyVMOptionsPatch {
  addSystemProperty("my.feature.enabled", "true")
}
```
