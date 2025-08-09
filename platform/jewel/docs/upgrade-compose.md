# Guide to upgrading Compose in IntelliJ Platform

The upgrade process primarily involves updating artefact version numbers, and potentially adding new dependency entries,
across multiple build configuration files.
There are three build systems that are impacted by an update:

* JPS — the current build system for the IJ Platform
* Bazel — the future build system for the IJ Platform
* Gradle — used by Jewel for static analysis purposes

Most of the changes can be performed in the community repository; however, some are only possible in the monorepo and
must be performed by someone at JetBrains with the necessary access.

Keep in mind that major Compose upgrades may require a lot of time to get right.

## In the community repository

The major steps in the upgrade that can be:

1. Decide the new Compose version
2. Update the Gradle build
3. Update the JPS build
4. Update the Bazel build
5. Post-update cleanup & verification

### 1. Identify the Target Compose Version

Determine the specific Compose version you intend to upgrade to. We try to use CMP versions with stability of beta or
better,
only using alphas if we have a specific need and our timelines don't allow us to wait for a beta.

The list of CMP releases is here: https://github.com/JetBrains/compose-multiplatform/releases

### 2. Update the Gradle build

Update the [`libs.versions.toml` version catalog](../gradle/libs.versions.toml) with the new Compose version.

That should cover the Gradle build update. Make sure the Gradle build still works correctly, e.g., by running the
`build` task.

### 3. Update the JPS build

The JPS build is updated from within IntelliJ IDEA. You can do these changes manually in the `iml` files, but it is
extremely unpractical.

1. Open the [intellij-community](../../..) project in IntelliJ IDEA
2. Open the Project Structure dialog
3. Navigate to _Modules_ > _intellij.libraries.compose.foundation.desktop_ module
4. Double-click the _compose-foundation-desktop_ library
5. Click the _Edit_ button, pick the new version and follow the instructions. Make sure the _sources_ and _Javadoc_
   options are selected, and leave the rest to the defaults.
6. Note down the Skiko version used by the CMP dependency. You can do that
   from [here](https://mvnrepository.com/artifact/org.jetbrains.compose.foundation/foundation),
   or by clicking the link next to the _Transitive dependencies_ checkbox and searching for _skiko_ in the dialog that
   shows up.

   > [!IMPORTANT]
   > Do not make any changes in the _Transitive dependencies_ dialog — click _Cancel_ when you've found the Skiko
   version to use.
7. Navigate to the _intellij.platform.jewel.ui_ module
8. Update the _org.jetbrains.compose.components.components.resources_ and
   _org.jetbrains.compose.components.components.resources.desktop_ dependencies to the same CMP version with the same
   procedure as above
9. Now it's time to update the Skiko dependency. Go to the _intellij.libraries.skiko_ module
10. If the Skiko version currently listed is different from the one CMP requires — which is usually the case — then
    follow the same procedure as above to upgrade Skiko to the required version. Note that the required Skiko version
    may not be the latest one.

You should now be able to see the changes in `.iml` files that correspond to the upgrades. If they do not immediately
show up, you can use the _Save all_ action to force the IDE to write changes to disk.

### 4. Update the Bazel build

The Bazel build is generated from the JPS one; updating this is just a matter of running the [
`jpsModelToBazelCommunityOnly.cmd`](../../../build/jpsModelToBazelCommunityOnly.cmd) script. The script will update a
number of `BUILD.bazel` and `MODULE.bazel` files.

Double-check manually that the script only makes changes that match the JPS changes. Revert any spurious change there
may be.

### 5. Post-update cleanup & verification

It may not be required, but it could be a good idea to regenerate the icon classes by running the _Generate icon
classes (ex icons.gant)_ run configuration. It probably will not result in any changes — but if it does, those changes
must be thoroughly inspected before committing.

After you're done with the steps above, it's time to verify that the upgrade was successful. To do so, you can run the
GitHub CI checks locally (see [`jewel-checks.yml`](../../../.github/workflows/jewel-checks.yml)), and then thoroughly
smoke test the standalone and IDE samples. If you find any regression, notify the team immediately.

You should also run all the IJP tests using the [`tests.cmd`](../../../tests.cmd) script in the community repo root.

## In the JetBrains monorepo

There are a few steps in the process that can only be performed by JetBrains employees in the monorepo:

1. Update the expected build contents; the `build/expected/ultimate-content-platform.yaml` file defines the expected
   content of the ultimate platform. This may beed to be updated
2. If the Skiko version has changed, you need to make sure the Skiko binaries are properly signed
3. Build and run monorepo tests, either locally or on TeamCity
4. Safe push the branch 🤞
