# Detekt Compose rules

This is the Detekt Compose rules JAR. It is used for static analysis in the IDE and by the Gradle build.

For more info see [this page](https://mrmans0n.github.io/compose-rules/detekt/).
Current rules JAR version: [0.4.26](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.26)

## Getting findings in the IDE editor

1. Install the [Detekt IntelliJ plugin](https://plugins.jetbrains.com/plugin/10761-detekt)
2. Run `./install-rules-to-idea.sh` from this directory

![Detekt plugin configuration example](detekt-plugin-config.png)

## Updating the rules

To update the rules JAR:

1. Go to https://github.com/mrmans0n/compose-rules/releases/latest
2. Download the `detekt-compose-[...]-all.jar` file
3. Rename it to `detekt-compose.jar`
4. Replace the file in this folder with the new one
5. Update the version number and link above
