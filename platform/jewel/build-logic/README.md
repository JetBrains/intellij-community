# Convention Plugins

The `build-logic` folder defines project-specific convention plugins, used to keep a single
source of truth for common module configurations.

This approach is heavily based on [Now in Android](https://github.com/android/nowinandroid)'s approach, in turn based on
[https://developer.squareup.com/blog/herding-elephants/](https://developer.squareup.com/blog/herding-elephants/)
and
[https://github.com/jjohannes/idiomatic-gradle](https://github.com/jjohannes/idiomatic-gradle).

By setting up convention plugins in `build-logic`, we can avoid duplicated build script setup,
messy `subproject` configurations, without the pitfalls of the `buildSrc` directory.

`build-logic` is an included build, as configured in the root
[`settings.gradle.kts`](../settings.gradle.kts).

Inside `build-logic` is a `convention` module, which defines a set of plugins that all normal
modules can use to configure themselves.

`build-logic` also includes a set of `Kotlin` files used to share logic between plugins themselves,
which is most useful for configuring Android components (libraries vs applications) with shared
code.

These plugins are *additive* and *composable*, and try to only accomplish a single responsibility.
Modules can then pick and choose the configurations they need.
If there is one-off logic for a module without shared code, it's preferable to define that directly
in the module's `build.gradle`, as opposed to creating a convention plugin with module-specific
setup.

Current list of convention plugins:

- [`jewel.application`](convention/src/main/kotlin/ApplicationConventionPlugin.kt),
  [`jewel.library`](convention/src/main/kotlin/LibraryConventionPlugin.kt),
  [`jewel.test`](convention/src/main/kotlin/TestConventionPlugin.kt):
  Configure common Kotlin options.
- [`jewel.application.compose`](convention/src/main/kotlin/ApplicationComposeConventionPlugin.kt),
  [`jewel.library.compose`](convention/src/main/kotlin/LibraryComposeConventionPlugin.kt):
  Configure Compose options
