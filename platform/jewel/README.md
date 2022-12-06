# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the _Darcula_ and _New UI_ Swing Look and Feels used on the IntelliJ Platform into Compose for Desktop.

## Project structure

The project is split in modules:

1. `core` is the base Jewel library code (composables, interface definitions, etc.)
2. `compose-utils` is a collection of utilities for dealing with Compose, and Swing interop
3. `themes` are the two themes implemented by Jewel:
    1. `darcula` is the old school Intellij LaF, called Darcula, which has two implementations:
        1. `darcula-standalone` is the base theme and can be used in any Compose for Desktop project
        2. `darcula-ide` is a version of the theme that can be used in an IDEA plugin, and integrates with the IDE's Swing LaF and themes via a
           bridge (more
           on that later)
    2. `new-ui` implements the new IntelliJ LaF, known as "new UI". This also has the same two implementations
4. `samples` contains the example apps, which showcase the available components:
    1. `standalone` is a regular CfD app, using the predefined "base" theme definitions
    2. `ide-plugin` is an IntelliJ plugin, adding some UI to the IDE, and showcasing the use of the bridge (see later)

### Running the samples

To run the stand-alone sample app, you can run the `:samples:standalone:run` Gradle task.

To run the IntelliJ IDEA plugin sample, you can run the `:samples:ide-plugin:runIde` Gradle task. This will download and run a copy of IJ Community
with the plugin installed; you can check the additional panels in the IDE once it starts up (at the bottom, by default, in old UI; in the overflow
in the new UI).

If you're using IntelliJ IDEA, you can use the "Stand-alone sample" and "IDE sample" run configurations.

### The Swing Bridge

In the `*-ide` modules, there is a crucial element for proper integration with the IDE: a bridge between the Swing theme and LaF, and the Compose
world.
This bridge ensures that we pick up the colours, typography, metrics, and images as defined in the current IntelliJ theme, and apply them to the
Compose theme as well.

The work of building this bridge is fairly complex as there isn't a good mapping between the IDE LaF properties, the Darcula design specs, and the
Compose implementations. Sometimes, you will need to get a bit creative.

When adding a new composable to the IJ theme, you need to make sure you also update the bridge to properly support it at runtime. You can refer to the
[Darcula design specs](https://jetbrains.design/intellij) and corresponding [Figma specs](https://jetbrains.design/intellij/resources/UI_kit/), but
the ultimate goal is consistency with the Swing implementation, so the ground truth of what you see in the IDE is the reference for any implementation
and trumps the specs.

To find the required values in the IDE, we recommend enabling
the [IDE internal mode](https://plugins.jetbrains.com/docs/intellij/enabling-internal.html)
and using the [UI Inspector](https://plugins.jetbrains.com/docs/intellij/internal-ui-inspector.html) and
[LaF Defaults](https://plugins.jetbrains.com/docs/intellij/internal-ui-laf-defaults.html) tools to figure out the names of the parameters to use in
the bridge.
