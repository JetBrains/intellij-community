---
name: registry
description: Guidelines for using the IntelliJ Registry API. Use when working with registry keys or feature flags.
---

# Working with Registry

Guidelines for using the IntelliJ Registry API.

## Documentation

- **Defining Registry Keys**: [Registry.md](../docs/IntelliJ-Platform/4_man/Registry.md) - How to declare keys in `plugin.xml` or `registry.properties`
- **Cloud Registry**: [Cloud Registry and Notifications.md](../docs/IntelliJ-Platform/4_man/Cloud%20Registry%20and%20Notifications.md) - Remote registry updates for JetBrains IDEs

## Quick Reference

### Defining a Registry Key

Always prefer declaring in `plugin.xml` (not `registry.properties`):

```xml
<registryKey key="my.feature.enabled"
             defaultValue="true"
             description="Enables my feature"
             restartRequired="false"/>
```

To override in a dependent plugin:
```xml
<registryKey key="my.feature.enabled"
             defaultValue="false"
             description="Enables my feature"
             restartRequired="false"
             overrides="true"/>
```

### Accessing the Registry
In suspending code:
```kotlin
val isEnabled = RegistryManager.getInstanceAsync().get("my.key")
```

In blocking code:
```kotlin
val isEnabled = RegistryManager.getInstance().get("my.key")
```

Access via `Registry.get()` or `Registry.is()` is effectively deprecated, since it might cause problems during early IDE startup.
Always prefer the `RegistryManager` when possible.

### Early Startup: Registry.is() with Default Value

When code may run before `COMPONENTS_LOADED` state (e.g., during EULA dialog, splash screen),
you MUST use `Registry.is(key, defaultValue)` with an explicit default:

```kotlin
// Required for early startup code
Registry.`is`("my.key", false)  // default must match registry.properties
```

**How to find the default value:**
1. Search in `community/platform/util/resources/misc/registry.properties`
2. Or check the `<registryKey>` declaration in `plugin.xml`

**Why:** `Registry.is(key)` without default throws an exception if called before
`LoadingState.COMPONENTS_LOADED`. The safe overload returns the provided default
when Registry is not yet initialized.

### Override via Command Line

For testing or run configurations:
```
-Dmy.registry.key=value
```

## Registry in Tests

Use `@RegistryKey` annotation instead of `Registry.get().setValue()`:

```kotlin
@Test
@RegistryKey(key = "my.registry.key", value = "true")
fun testWithRegistryEnabled() { ... }
```

See [writing-tests.md](../writing-tests/SKILL.md#registry-values-in-tests) for more test patterns.
