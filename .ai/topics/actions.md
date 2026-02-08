# Actions

Guidelines for implementing IntelliJ actions (`AnAction`).

## Documentation

- [Action System](../docs/IntelliJ-Platform/4_man/Action-System.md) - Architecture and usage of the action system

## Best Practices

- **NEVER instantiate Presentation in action constructors** - Use no-argument constructors
- **Define text, description, and icon in plugin.xml** - Not in constructor parameters
- **Follow the convention:**
  1. Set the `id` attribute for the action in plugin.xml
  2. Optionally set the `icon` attribute if an icon is needed
  3. Set text and description in the message bundle (e.g., GitBundle.properties):
    - `action.<action-id>.text=Translated Action Text`
    - `action.<action-id>.description=Translated Action Description`

**Good example:**

Kotlin:
```kotlin
class MyAction : AnAction()
```

plugin.xml:
```xml
<action id="My.Action.Id"
        class="my.package.MyAction"
        icon="my.package.MyIcons.ICON"/>
```

Bundle.properties:
```properties
action.My.Action.Id.text=My Action
action.My.Action.Id.description=Description of my action
```
