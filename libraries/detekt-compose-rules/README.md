### Detekt Compose Rules

This module defines custom Detekt rules for Jetpack Compose used in the [Jewel codebase](../../platform/jewel).

Usage:
- This module exists only to produce a JAR file used to provide rules to a Detekt plugin.
- Do not add it as a runtime or compile dependency of other modules.
- Consume the resulting JAR via Detekt IDEA plugin.
