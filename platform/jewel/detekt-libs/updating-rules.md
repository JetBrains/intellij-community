# Detekt Compose rules

This is the Detekt Compose rules JAR. It is used for static analysis in the IDE and by the Gradle build.

Why a jar in the repo? The IDE plugin does not support dynamically getting it from Maven Central, so at this point we
may as well just be using this jar for both the IDE plugin and the Gradle build.

For more info see [this page](https://mrmans0n.github.io/compose-rules/detekt/).
Current rules JAR version: [0.4.26](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.26)

## Getting findings in the IDE editor

1. Install the [Detekt IntelliJ plugin](https://plugins.jetbrains.com/plugin/10761-detekt)
2. Run `bazel build //platform/jewel/detekt-plugin:detekt-plugin-binary_deploy.jar`
3. Run `bazel build //libraries/detekt-compose-rules:detekt-compose-rules-binary_deploy.jar`
4. Open [_Settings | Tools | detekt_](jetbrains://idea/settings?name=Tools--detekt)
5. Enable background analysis
6. Point it to the [`detekt.yml`](../detekt.yml) configuration file
7. Add the [`detekt-compose.jar`](../../../out/bazel-bin/libraries/detekt-compose-rules/detekt-compose-rules-binary_deploy.jar) file as a plugin
8. Add the [`detekt-plugin-binary_deploy.jar`](../../../out/bazel-bin/platform/jewel/detekt-plugin/detekt-plugin-binary_deploy.jar)
   file as a plugin

![Detekt plugin configuration example](detekt-plugin-config.png)

## Updating the rules

To update the rules JAR:

1. Go to https://github.com/mrmans0n/compose-rules/releases/latest
2. Download the `detekt-compose-[...]-all.jar` file
3. Rename it to `detekt-compose.jar`
4. Replace the file in this folder with the new one
5. Update the version number and link above
