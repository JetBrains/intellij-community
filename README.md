# intellij-diff-plugin

Syntax highlighting for .diff and .patch files in IntelliJ IDEA and [other JetBrains IDEs](https://www.jetbrains.com/products.html)

[Download from plugins.jetbrains.com](https://plugins.jetbrains.com/plugin/11957-diff--patch-file-support).


## Development

This project was built using Java 8 and Gradle 4.8.

In order to open this project, follow these steps:

1. Install [Java 8 JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
1. Install [Gradle](https://gradle.org/install/)
1. [Define](https://www.jetbrains.com/help/idea/sdk.html#define-sdk) a project SDK named *IDEA JDK*. If you have a Java 8 SDK already, you may choose to rename or clone it.
1. Open the project.
1. IntelliJ will ask you to import the project as a Gradle project. Confirm and accept the defaults.

Now, there are two run configurations, *run in IDE* and *build plugin*.

* *run in IDE* allows you to launch an IntelliJ Community Edition with the plugin enabled.
* *build plugin* will generate `build/distributions/intellij-diff-plugin-*.zip`, which you can install in your JetBrains IDE.

To find out more about plugin development, please refer to the excellent official documentation:
http://www.jetbrains.org/intellij/sdk/docs/
