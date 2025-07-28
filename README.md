[![official JetBrains project](http://jb.gg/badges/official.svg)](https://github.com/JetBrains/.github/blob/main/profile/README.md) [![Build status](https://github.com/JetBrains/intellij-community/workflows/IntelliJ%20IDEA/badge.svg)](https://github.com/JetBrains/intellij-community/actions/workflows/IntelliJ_IDEA.yml)

# IntelliJ IDEA Open Source

This repository is the open-source part of the IntelliJ IDEA codebase.
It also serves as the basis for [IntelliJ Platform development](https://www.jetbrains.com/opensource/idea). 

These instructions will help you build IntelliJ IDEA from source code.

If you are new to the community and would like to contribute code or help others learn, see [CONTRIBUTING.md](https://github.com/JetBrains/intellij-community/blob/master/CONTRIBUTING.md) to get started.

The following conventions will be used to refer to directories on your machine:
* `<USER_HOME>` is your OS user's home directory.
* `<IDEA_HOME>` is the root directory for the **IntelliJ source code**.

___
## Getting the IntelliJ IDEA Source Code

This section will guide you through getting the project sources and help avoid common issues in git config and other steps before opening it in the IDE.

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

IntelliJ IDEA source code is available from the [GitHub repository](https://github.com/JetBrains/intellij-community), which can be cloned or downloaded as a zip file (based on a branch) into `<IDEA_HOME>`. 
The **master** (_default_) branch contains the source code which will be used to create the next major version of IntelliJ IDEA. 
The branch names and build numbers for older releases of IntelliJ IDEA can be found on the
[Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) page.

You can [clone this project](https://www.jetbrains.com/help/idea/manage-projects-hosted-on-github.html#clone-from-GitHub) directly using IntelliJ IDEA. 

Alternatively, follow the steps below in a terminal:

   ```
   git clone https://github.com/JetBrains/intellij-community.git
   cd intellij-community
   ```

> [!TIP]
> - **For faster download**: If the complete repository history isn't needed, create [shallow clone](https://git-scm.com/docs/git-clone#Documentation/git-clone.txt---depthdepth)
> To download only the latest revision of the repository,  add `--depth 1` option after `clone`.
> - Cloning in IntelliJ IDEA also supports creating shallow clone.

#### Get Android Modules
IntelliJ IDEA requires additional Android modules from separate Git repositories.

Run the following script from project root `<IDEA_HOME>` to get the required modules:
- Linux/macOS: `./getPlugins.sh`
- Windows: `getPlugins.bat`

> [!IMPORTANT]
>
>  Always `git checkout` the `intellij-community` and `android` Git repositories to the same branches/tags.


---
## Building IntelliJ IDEA
These instructions will help you build IntelliJ IDEA from source code, which is the basis for IntelliJ Platform development.
IntelliJ IDEA '**2023.2**' or newer is required.

### Opening the IntelliJ IDEA Source Code in the IDE
Using the latest IntelliJ IDEA, click '**File | Open**', select the `<IDEA_HOME>` directory.
If IntelliJ IDEA displays a message about a missing or out-of-date required plugin (e.g. Kotlin),
[enable, upgrade, or install that plugin](https://www.jetbrains.com/help/idea/managing-plugins.html) and restart IntelliJ IDEA.


### Build Configuration Steps
1. **JDK Setup**
  - Use JetBrains Runtime 17 (without JCEF) to compile
  - IDE will prompt to download it on the first build
> [!IMPORTANT]
>
> JetBrains Runtime **without** JCEF is required. If `jbr-17` SDK points to JCEF version, change it to the non-JCEF version:
> - Add `idea.is.internal=true` to `idea.properties` and restart the IDE.
> - Go to '**Project Structure | SDKs**'
> - Click 'Browse' → 'Download...'
> - Select version 17 and vendor 'JetBrains Runtime'
> - To confirm if the JDK is correct, navigate to the SDK page with jbr-17 selected. Search for `jcef`, it should **_NOT_** yield a result.

2. **Maven Configuration** : If the **Maven** plugin is disabled, [add the path variable](https://www.jetbrains.com/help/idea/absolute-path-variables.html) "**MAVEN_REPOSITORY**" pointing to `<USER_HOME>/.m2/repository` directory.

3. **Memory Settings**
  - Ensure a minimum **8GB** RAM on your computer.
  - With the minimum RAM, disable "**Compile independent modules in parallel**" in '**Settings | Build, Execution, Deployment | Compiler**'.
  - With notably higher available RAM, Increase "**User-local heap size**" to `3000`.


### Building the IntelliJ IDEA Application from Source

**To build IntelliJ IDEA from source**, choose '**Build | Build Project**' from the main menu.

**To build installation packages**, run the [installers.cmd](installers.cmd) script in `<IDEA_HOME>` directory. `installers.cmd` will work on both Windows and Unix systems.
Options to build installers are passed as system properties to `installers.cmd` command.
You may find the list of available properties in [BuildOptions.kt](platform/build-scripts/src/org/jetbrains/intellij/build/BuildOptions.kt)

Installer build examples:
```bash
# Build installers only for current operating system:
./installers.cmd -Dintellij.build.target.os=current
```
```bash
# Build source code _incrementally_ (do not build what was already built before):
./installers.cmd -Dintellij.build.incremental.compilation=true
```

>[!TIP]
> 
> The `installers.cmd` is used to run [OpenSourceCommunityInstallersBuildTarget](build/src/OpenSourceCommunityInstallersBuildTarget.kt) from the command line.
You can also call it directly from IDEA, using run configuration `Build IDEA Community Installers (current OS)`.


#### Dockerized Build Environment
To build installation packages inside a Docker container with preinstalled dependencies and tools, run the following command in `<IDEA_HOME>` directory (on Windows, use PowerShell):
```bash
docker run --rm -it --user "$(id -u)" --volume "${PWD}:/community" "$(docker build --quiet . --target intellij_idea)"
```
> [!NOTE]
> 
> Please remember to specify the `--user "$(id -u)"` argument for the container's user to match the host's user.
> This prevents issues with permissions for the checked-out repository, the build output, and the mounted Maven cache, if any.
> 
To reuse the existing Maven cache from the host system, add the following option to `docker run` command:
`--volume "$HOME/.m2:/home/ide_builder/.m2"`

---
## Running IntelliJ IDEA
To run the IntelliJ IDEA that was built from source, choose '**Run | Run**' from the main menu. This will use the preconfigured run configuration `IDEA`.

To run tests on the build, apply these settings to the '**Run | Edit Configurations... | Templates | JUnit**' configuration tab:
* Working dir: `<IDEA_HOME>/bin`
* VM options:  `-ea`


#### Running IntelliJ IDEA in CI/CD environment

To run tests outside of IntelliJ IDEA, run the `tests.cmd` command in `<IDEA_HOME>` directory.`tests.cmd` can be used in both Windows and Unix systems.
Options to run tests are passed as system properties to `tests.cmd` command.
You may find the list of available properties in [TestingOptions.kt](platform/build-scripts/src/org/jetbrains/intellij/build/TestingOptions.kt)

```bash
# Build source code _incrementally_ (do not build what was already built before): `
./tests.cmd -Dintellij.build.incremental.compilation=true
```
```bash
#Run a specific test: 
./tests.cmd -Dintellij.build.test.patterns=com.intellij.util.ArrayUtilTest
```

`tests.cmd` is used just to run [CommunityRunTestsBuildTarget](build/src/CommunityRunTestsBuildTarget.kt) from the command line.
You can also call it directly from IDEA, see run configuration `tests in community` for an example.