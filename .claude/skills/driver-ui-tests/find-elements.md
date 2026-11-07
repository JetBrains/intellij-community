# Find a UI Element

Read this file when you look for a component, or when a search finds no element or too many.

## Element Selectors

| Selector | Usage |
|----------|-------|
| `byAccessibleName("name")` | Find by accessible name attribute |
| `byJavaClass("com.example.MyPanel")` | Find by the full class name |
| `byType("javax.swing.JTree")` | Find by the full class name or a supertype |
| `byVisibleText("text")` | Find by visible text content |

Use `byJavaClass` or `byType`. Do not use `byClass`, which is deprecated. A full class name has `@Language("jvm-class-name")`, so the IDE resolves it and updates it on rename.

Write the class name inside the query. Do not put it, or a full XPath, into a separate constant: the IDE cannot resolve or rename a class name in a plain string.

```kotlin
// BAD - a short class name in a separate constant
private const val POPUP_LIST_XPATH = "//div[@class='HeavyWeightWindow']//div[@class='MyList']"

// GOOD - a private helper with a resolvable class name
private fun Finder.optionPopup() = x { byType("com.intellij.ui.components.JBList") }
```

## Scoping Element Searches

When multiple elements match a selector, scope to a parent element:

```kotlin
// BAD - will fail if multiple InstallButtons exist
ui.x { byJavaClass("com.intellij.ide.plugins.newui.InstallButton") }.click()

// GOOD - scope to a parent element first
val detailPane = ui.x { byJavaClass("com.intellij.ide.plugins.newui.PluginDetailsPageComponent") }
detailPane.x { byJavaClass("com.intellij.ide.plugins.newui.InstallButton") }.click()
```

## Page Object Pattern or UiComponent

Page objects extend `UiComponent` with `ComponentData` constructor:

```kotlin
class MyPageObject(data: ComponentData) : UiComponent(data) {
    val myButton = x { byAccessibleName("Button Name") }
    val myPanel = x { byType("com.example.MyPanel") }

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

## Finding toolbar / title-bar actions

Toolbar and tool-window title actions that show their text (`presentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)`) render as **`ActionButtonWithText`**, not `ActionButton`. The SDK `actionButton(text)` helper searches `@class='ActionButton'` only, so it silently never matches them. `ActionButtonWithText` extends `ActionButton`, so `byType` matches both variants:

```kotlin
// Matches both icon-only and text-bearing action buttons
fun Finder.statusButton(text: String) =
  x { byType("com.intellij.openapi.actionSystem.impl.ActionButton") and byVisibleText(text) }
```

The visible text is itself a reliable assertion signal — you usually do not need to read the backing service/state.

## Keyboard Interactions

```kotlin
element.keyboard { typeText("search text") }
ui.keyboard { key(KeyEvent.VK_ENTER) }
ui.keyboard { hotKey(KeyEvent.VK_META, KeyEvent.VK_COMMA) }  // Cmd+,
```

## Reading IDE state via `@Remote`

To read state from a service or model in the IDE under test, declare a `@Remote` interface and call it via `driver.service(...)` / `driver.utility(...)`.

- **Plugin classes need the `plugin` field.** Without it the class resolves against the platform/core classloader → `DriverIllegalStateException: No such class '<fqn>' in plugin null`.
  - Class in a plugin **content module**: `@Remote("<fqn>", plugin = "<plugin.id>/<content.module>")` (e.g. `com.intellij.figma/intellij.figma.core`).
  - Class in the **main / embedded** plugin module: `@Remote("<fqn>", plugin = "<plugin.id>")`.
- **Method dispatch resolves against the DECLARED `@Remote` class, not the runtime object.** A method declared on a sealed/abstract supertype ref is "not found" at runtime — declare it on the concrete subtype, or expose it via a top-level type. (The `jvm-class-name` injection also cannot resolve a nested `Foo$Bar` name → a cosmetic "Cannot resolve class" inspection error; prefer top-level types.)
- Add the plugin module as a TEST dependency so the FQNs resolve for code-insight.

```kotlin
@Remote("com.example.MyAppService", plugin = "com.example.myplugin/com.example.myplugin.core")
interface MyAppServiceRef {
  fun getConfig(): MyConfigRef
}
// driver.service(MyAppServiceRef::class).getConfig()...
```
