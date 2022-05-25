# intellij-diff-plugin

Syntax highlighting for .diff and .patch files in IntelliJ IDEA and [other JetBrains IDEs](https://www.jetbrains.com/products.html).

## Download

- [Download from plugins.jetbrains.com](https://plugins.jetbrains.com/plugin/11957-diff--patch-file-support)
- [Download from github](https://github.com/ThomasR/intellij-diff-plugin/releases)
- or simply use IntelliJ's plugin manager and search for *Diff / Patch File Support*


## Development

This project was built using Java 11 and Gradle 6.7.1.

In order to open this project in IntelliJ IDEA, follow these steps:

1. Install [Java 11 JDK](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
1. [Define](https://www.jetbrains.com/help/idea/sdk.html#define-sdk) a project SDK named *11*. If you have a Java 11 SDK already, you may choose to rename or clone it.
1. Open the project and wait for IntelliJ to download the required libraries.  
  (This may take a long time, because several hundred MBs are downloaded.)

Now, there are two [run configurations](https://www.jetbrains.com/help/idea/creating-and-editing-run-debug-configurations.html#e867c088), *Run in IDE* and *Build Plugin*.

* *Run in IDE* allows you to launch an IntelliJ Community Edition with the plugin enabled.  
  The IDE version is configured in `build.gradle`. Required files will be automatically downloaded.  
  You can find version numbers here: [stable](https://www.jetbrains.com/intellij-repository/releases) | [snapshots](https://www.jetbrains.com/intellij-repository/snapshots/).
  See section *com.jetbrains.intellij.idea*. You can use *Version* or *Build Number*.
* *Build Plugin* will generate `build/distributions/intellij-diff-plugin-*.zip`, which you can [install](https://www.jetbrains.com/help/idea/managing-plugins.html#c5e86b83) in your JetBrains IDE.


To find out more about plugin development, please refer to the excellent official documentation:
http://www.jetbrains.org/intellij/sdk/docs/

### Troubleshooting

If you develop under Windows, and see the warning

```
WARNING: Could not open/create prefs root node Software\JavaSoft\Prefs
at root 0x80000002. Windows RegCreateKeyEx(...) returned error code 5.
```

then simply run the file [`fix-jdk-warning-in-windows.reg`](fix-jdk-warning-in-windows.reg), and confirm the dialogs.
