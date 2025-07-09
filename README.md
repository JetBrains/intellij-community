[![official JetBrains project](http://jb.gg/badges/official.svg)](https://github.com/JetBrains/.github/blob/main/profile/README.md) [![Build status](https://github.com/JetBrains/intellij-community/workflows/IntelliJ%20IDEA/badge.svg)](https://github.com/JetBrains/intellij-community/actions/workflows/IntelliJ_IDEA.yml)

# IntelliJ IDEA Community Edition

IntelliJ IDEA Community Edition is a free IDE built on open-source code that provides essential features for Java and Kotlin enthusiasts.

These instructions will help you build IntelliJ IDEA Community Edition from source code, which is the basis for [IntelliJ Platform development](https://www.jetbrains.com/opensource/idea). If you are new to the community and would like to contribute code or help others learn, see [CONTRIBUTING.md](https://github.com/dextro67/intellij-community/blob/master/CONTRIBUTING.md) to get started.

The following conventions will be used to refer to directories on your machine:
* `<USER_HOME>` is your OS user's home directory.
* `<IDEA_HOME>` is the root directory for the **IntelliJ source code**.

___
## Getting the IntelliJ Source Code

This section will guide you to get the project source faster, avoid common issues in git config and other steps before opening it in the IDE.

#### Prerequisites
- [Git](https://git-scm.com/) installed
- ~2GB free disk space
- Install [IntelliJ IDEA 2023.2](https://www.jetbrains.com/idea/download) or higher.
- For **Windows** set these git config to avoid common issues during cloning:
  ```
  git config --global core.longpaths true
  git config --global core.autocrlf input
  ```

#### Clone Main Repository

IntelliJ IDEA Community Edition source code is available from [github repository](https://github.com/JetBrains/intellij-community) by either cloning or
downloading a zip file (based on a branch) into `<IDEA_HOME>`. The **master** (_default_) branch contains the source code which will be used to create the next major version of IntelliJ IDEA. The branch names
and build numbers for older releases of IntelliJ IDEA can be found on the page of
[Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html).

You can directly [checkout this project](https://www.jetbrains.com/help/idea/manage-projects-hosted-on-github.html#clone-from-GitHub) using an installed IntelliJ IDEA. Or you may follow the steps below for terminal.

   ```
   git clone https://github.com/JetBrains/intellij-community.git
   cd intellij-community
   ```

#### Get Android Modules
IntelliJ IDEA Community Edition requires additional Android modules from separate Git repositories.

Run the following script from project root `<IDEA_HOME>` to get the required module:
- Linux/macOS: `./getPlugins.sh`
- Windows: `getPlugins.bat`

> [!IMPORTANT]
> ##### Always `git checkout` the `intellij-community` and `android` Git repositories to the same branches/tags.

> [!TIP]
> - **For faster download**: If the complete repository history isn't needed add `--depth 1` after `clone`.
> - If the complete repository history isn't needed add `--shallow` flag for faster download


---
## Building IntelliJ Community Edition
These instructions will help you build IntelliJ IDEA Community Edition from source code, which is the basis for IntelliJ Platform development.
Version '**2023.2**' or newer of IntelliJ IDEA Community Edition or IntelliJ IDEA Ultimate Edition is required to build and develop
for the IntelliJ Platform.

### Opening the IntelliJ Source Code in the IDE
Using the latest IntelliJ IDEA IDE , click '**File | Open**', select the `<IDEA_HOME>` directory.
If IntelliJ IDEA displays an message about a missing or out of date required plugin (e.g. Kotlin),
[enable, upgrade, or install that plugin](https://www.jetbrains.com/help/idea/managing-plugins.html) and restart IntelliJ IDEA.


### Build Configuration Steps
1. **JDK Setup**
  - Use JetBrains Runtime 17 (without JCEF) to compile
  - IDE will prompt to download it on first build
> [!IMPORTANT]
> JetBrains Runtime **without** JCEF is required. If `jbr-17` SDK points to JCEF version,change it to the non-JCEF version:
> - Add `idea.is.internal=true` to `idea.properties`
> - Go to '**Project Structure | SDKs**'
> - Click 'Browse' → 'Download...'
> - Select version 17 and vendor 'JetBrains Runtime'
> - To confirm if the JDK is correct, navigate to the SDK page with jbr-17 selected. Search for `jcef`, it should **_not_** yield a result.

2. **Maven Configuration** : If the **Maven** plugin is disabled, [add the path variable](https://www.jetbrains.com/help/idea/absolute-path-variables.html) "**MAVEN_REPOSITORY**" pointing to `<USER_HOME>/.m2/repository` directory.

3. **Memory Settings**
  - Ensure minimum **8GB** RAM on your computer.
  - With minimum RAM configure: Disable "**Compile independent modules in parallel**" in '**Settings | Build, Execution, Deployment | Compiler**'.
  - With a notably higher available RAM: Increase "**User-local heap size**" to `3000`.


### Building the IntelliJ Application from Source

**To build IntelliJ IDEA Community Edition from source**, choose '**Build | Build Project**' from the main menu.

**To build installation packages**, run the [installers.cmd](installers.cmd) script in `<IDEA_HOME>` directory. `installers.cmd` will work on both Windows and Unix systems.
Options to build installers are passed as system properties to `installers.cmd` command.
You may find the list of available properties in [BuildOptions.kt](platform/build-scripts/src/org/jetbrains/intellij/build/BuildOptions.kt)

Installer build examples:
```bash
# Build installers only for current operating system:
./installers.cmd -Dintellij.build.target.os=current

# Build source code _incrementally_ (do not build what was already built before):
./installers.cmd -Dintellij.build.incremental.compilation=true
```

**Tip**: The `installers.cmd` is used to run [OpenSourceCommunityInstallersBuildTarget](build/src/OpenSourceCommunityInstallersBuildTarget.kt) from the command line.
You may call it directly from IDEA, using run configuration `Build IDEA Community Installers (current OS)`.


#### Dockerized Build Environment
To build installation packages inside a Docker container with preinstalled dependencies and tools, run the following command in `<IDEA_HOME>` directory (on Windows, use PowerShell):
```bash
docker run --rm -it --user "$(id -u)" --volume "${PWD}:/community" "$(docker build --quiet . --target intellij_idea)"
```
> [!NOTE]
> Please remember to specify the `--user "$(id -u)"` argument for the container's user to match the host's user.
> This is required not to affect the permissions of the checked-out repository, the build output and the mounted Maven cache, if any.

To reuse the existing Maven cache from the host system, add the following option to `docker run` command:
`--volume "$HOME/.m2:/home/ide_builder/.m2"`

---
## Running IntelliJ IDEA
To run the IntelliJ IDEA built from source, choose '**Run | Run**' from the main menu. This will use the preconfigured run configuration `IDEA`.

To run tests on the build, apply these setting to the '**Run | Edit Configurations... | Templates | JUnit**' configuration tab:
* Working dir: `<IDEA_HOME>/bin`
* VM options:  `-ea`


#### Running IntelliJ IDEA in CI/CD environment

To run tests outside of IntelliJ IDEA, run the `tests.cmd` command in `<IDEA_HOME>` directory.`tests.cmd` can be used in both Windows and Unix systems.
Options to run tests are passed as system properties to `tests.cmd` command.
You may find the list of available properties in [TestingOptions.kt](platform/build-scripts/src/org/jetbrains/intellij/build/TestingOptions.kt)

```bash
# Build source code _incrementally_ (do not build what was already built before): `
./tests.cmd -Dintellij.build.incremental.compilation=true

#Run a specific test: 
./tests.cmd -Dintellij.build.test.patterns=com.intellij.util.ArrayUtilTest
```

`tests.cmd` is used just to run [CommunityRunTestsBuildTarget](build/src/CommunityRunTestsBuildTarget.kt) from the command line.
You may call it directly from IDEA, see run configuration `tests in community` for an example.