# IntelliJ IDEA Community Edition [![official JetBrains project](http://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
These instructions will help you build IntelliJ IDEA Community Edition from source code, which is the basis for IntelliJ Platform development.
The following conventions will be used to refer to directories on your machine:
* `<USER_HOME>` is your home directory.
* `<IDEA_HOME>` is the root directory for the IntelliJ source code.
* `<JDK_18_HOME>` is the root directory for the 1.8 JDK.

## Getting IntelliJ IDEA Community Edition Source Code
IntelliJ IDEA Community Edition source code is available from `github.com/JetBrains/intellij-community` by either cloning or
downloading a zip file (based on a branch) into `<IDEA_HOME>`. The default is the *master* branch. 

The master branch contains the source code which will be used to create the next major version of IntelliJ IDEA. The branch names
and build numbers for older releases of IntelliJ IDEA can be found on the page of
[Build Number Ranges](http://www.jetbrains.org/intellij/sdk/docs/basics/getting_started/build_number_ranges.html).

If you intend to make open-source contributions to the IntelliJ Platform,
see [Contributing to the IntelliJ Project](http://www.jetbrains.org/display/IJOS/Contribute) for more information.

_**Speed Tip:**_ If the complete repository history isn't needed then using a shallow clone (`git clone --depth 1`) will save significant time.

These Git operations can also be done through the [IntelliJ IDEA user interface](https://www.jetbrains.com/help/idea/using-git-integration.html).

IntelliJ IDEA Community Edition requires additional Android modules from separate Git repositories. To clone these repositories,
run one of the `getPlugins` scripts located in the `<IDEA_HOME>` directory. These scripts clone their respective *master* branches.
* `getPlugins.sh` for Linux or macOS.
* `getPlugins.bat` for Windows.

_**Note:**_ Always `git checkout` the `intellij-community` and `android` Git repositories to the same branches/tags. 

## Building IntelliJ Community Edition
Version 2020.1 or newer of IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate Edition is required to build and develop
for the IntelliJ Platform.

### Opening the IntelliJ Source Code for Build
Using IntelliJ IDEA **File | Open**, select the `<IDEA_HOME>` directory. 
* If IntelliJ IDEA displays an error about a missing or out of date required plugin (e.g. Kotlin),
  [enable, upgrade, or install that plugin](https://www.jetbrains.com/help/idea/managing-plugins.html) and restart IntelliJ IDEA.
* If IntelliJ IDEA displays an error about a Gradle configuration not found,
  [refresh the Gradle projects](https://www.jetbrains.com/help/idea/jetgradle-tool-window.html). 

### IntelliJ Build Configuration
JDK version 1.8 and JDK version 11 are required for building and developing for IntelliJ IDEA Community Edition.
1. Using IntelliJ IDEA, [configure](https://www.jetbrains.com/help/idea/sdk.html) a JDK named "**1.8**", pointing to `<JDK_18_HOME>`. The JDK
   should be of version 1.8.0_162 or newer; also it must include jfxrt.jar. You may use for example Oracle JDK or Amazon Corretto (the latter 
   can be downloaded directly from Project Structure dialog).
   * If not already present, add `<JDK_18_HOME>/lib/tools.jar` [to the Classpath](https://www.jetbrains.com/help/idea/sdk.html#manage_sdks) tab
     for the **1.8** JDK.
2. Configure a JDK named "**corretto-11**", pointing to installation of JDK 11. It's recommended to use Amazon Corretto JDK. You may 
   [download it directly](https://www.jetbrains.com/help/idea/sdk.html#jdk-from-ide) from Project Structure dialog.    
3. Also configure a JDK named "**IDEA jdk**" (case sensitive), pointing to installation of JDK 1.6. If you don’t want to install JDK 1.6
   then you may configure **IDEA jdk** to point to `<JDK_18_HOME>`. However, you must be careful to avoid using Java 8 APIs in IntelliJ IDEA Community Edition modules that use **IDEA jdk**. 
   * If not already present, add the corresponding path for tools.jar to the Classpath for "**IDEA jdk**" JDK.
4. If the _Maven Integration_ plugin is disabled, [add the path variable](https://www.jetbrains.com/help/idea/absolute-path-variables.html)
   "**MAVEN_REPOSITORY**" pointing to `<USER_HOME>/.m2/repository` directory.
5. _**Speed Tip:**_ If you have enough RAM on your computer,
   [configure the compiler settings](https://www.jetbrains.com/help/idea/specifying-compilation-settings.html)
   to enable the "Compile independent modules in parallel" option. Also, set the "User-local build process VM options" to `-Xmx2G`.
   These changes will greatly reduce the compile time.

### Building the IntelliJ Application Source Code
To build IntelliJ IDEA Community Edition from source, choose **Build | Build Project** from the main menu.

To build installation packages, run the `ant` command in `<IDEA_HOME>` directory. See the `build.xml` file for details.

## Running IntelliJ IDEA
To run the IntelliJ IDEA built from source, choose **Run | Run** from the main menu. This will use the preconfigured run configuration "**IDEA**".

To run tests on the build, apply these setting to the **Run | Edit Configurations... | Templates | JUnit** configuration tab:
  * Working dir: `<IDEA_HOME>/bin`
  * VM options: 
    * `-ea` 
    * `-Djava.system.class.loader=com.intellij.util.lang.UrlClassLoader` 
    * `-Didea.config.path=../test-config`
    * `-Didea.system.path=../test-system`
 
You can find other helpful information at [https://www.jetbrains.com/opensource/idea](https://www.jetbrains.com/opensource/idea).
The "Contribute Code" section of that site describes how you can contribute to IntelliJ IDEA.
