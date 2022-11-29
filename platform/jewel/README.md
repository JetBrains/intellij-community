# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the _Darcula_ Swing Look and Feel used on the IntelliJ Platform into Compose for Desktop. A bunch of shared code is extracted
to a separate module, `library`.

## Project structure

The project is split in modules:

1. `library` is the base Jewel library code (utils, interfaces, etc.)
2. `sample` is a stand-alone sample app of the Jewel themes
3. `themes` are the two themes implemented by Jewel:
    1. `intellij` is the Darcula theme, which has two implementations:
        1. `standalone` is the base theme and can be used in any Compose for Desktop project
        2. `idea` is a version of the theme that can be used in an IDEA plugin, and integrates with the IDE's Swing LaF and themes via a bridge (more
           on that later).

### Running the samples

To run the stand-alone sample app, you can run the `:sample:run` Gradle task.

To run the IntelliJ IDEA plugin sample, you can run the `:themes:intellij:idea:runIde` Gradle task. This will download and run a copy of IJ Community
with the plugin installed; you can check the JewelDemo panel in the IDE once it starts up (it's at the bottom, by default).

If you're in an IDE, you can use the "Stand-alone sample" and "IDE sample" run configurations.

### The Swing Bridge

In the `idea` module, there is a crucial element for proper integration with the IDE: a bridge between the Swing theme and LaF, and the Compose world.
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
