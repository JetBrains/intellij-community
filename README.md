# intellij-diff-plugin

Syntax highlighting for .diff and .patch files in IntelliJ IDEA and [other JetBrains IDEs](https://www.jetbrains.com/products.html)

[Download from plugins.jetbrains.com](https://plugins.jetbrains.com/plugin/11957-diff--patch-file-support).


## Development

This project was built using Java 8 and Gradle 5.2.

In order to open this project in IntelliJ IDEA, follow these steps:

1. Install [Java 8 JDK](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
1. [Define](https://www.jetbrains.com/help/idea/sdk.html#define-sdk) a project SDK named *IDEA JDK*. If you have a Java 8 SDK already, you may choose to rename or clone it.
1. Open the project and wait for IntelliJ to download the required libraries.

Now, there are two [run configurations](https://www.jetbrains.com/help/idea/creating-and-editing-run-debug-configurations.html#e867c088), *Run in IDE* and *Build Plugin*.

* *Run in IDE* allows you to launch an IntelliJ Community Edition with the plugin enabled.
* *Build Plugin* will generate `build/distributions/intellij-diff-plugin-*.zip`, which you can [install](https://www.jetbrains.com/help/idea/managing-plugins.html#c5e86b83) in your JetBrains IDE.
  Make sure to adjust `intellij/version` in `build.gradle`. Find valid version numbers here:
  [stable](https://www.jetbrains.com/intellij-repository/releases) | [snapshots](https://www.jetbrains.com/intellij-repository/snapshots/) | [previous](https://www.jetbrains.com/idea/download/previous.html)

To find out more about plugin development, please refer to the excellent official documentation:
http://www.jetbrains.org/intellij/sdk/docs/
