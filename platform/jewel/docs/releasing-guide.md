# Releasing new versions of Jewel

Releasing a new Jewel version is a complex process that involves cherry-picking changes from the master branch to the
target release branches, and it can only be done by someone at JetBrains with access to the monorepo. After the
cherry-picks are done and merged to the respective branches, they need to trigger the TeamCity job to publish the
artefacts to Maven Central.

Please ping Jakub, Nebojsa, or Sasha for help and guidance.

High-level steps:

## Before cutoff

1. Run the Gradle checks to make sure everything is ok:
   ```shell
   ../gradlew check detekt detektMain detektTest --continue
   ```
2. Verify that the current Metalava dumps are valid for the release:
   ```shell
   ../scripts/metalava-signatures.main.kts validate
   ```
3. Write the release notes for the release – use the script to get the draft:
   ```shell
   ../scripts/extract-release-notes.main.kts --since [yyyy-mm-dd]
   ```
    * The script collects the release notes from the PRs merged since the specified date (the actual date might not be
      aligned with the previous release's date, it might be earlier!)
    * The script creates a [`new_release_notes.md`](../new_release_notes.md) file in the Jewel root that you can copy in
      the [`RELEASE NOTES.md`](../RELEASE%20NOTES.md) file
    * Add the version header for your new release, and double check the CMP/IJP targets, then paste the script output
    * Clean up the stuff you pasted — make sure the format and tone are consistent with the other release notes
    * Also make sure you're including all the relevant commits in the release notes (e.g., some PRs may be missing the
      release notes or have them in an invalid format that isn't picked up), and that the release notes only include the
      notes for the commits actually included in the release. Cross-reference with the cherry-pick commits list if
      needed
4. Commit all changes and get them merged to `master`

## Cutoff

1. Cherry-pick the release-preparation changes to the target release branches (e.g., `261`). The cherry-pick point is
   the release cut.
   1. Make sure you've not included IJP major release-specific changes
   2. Update the Kotlin version in the [Gradle version catalog](../gradle/libs.versions.toml) to match the IJ Platform's Kotlin version
   3. Update other related versions if needed
   4. Run `./gradlew generateThemes --rerun-tasks` to update the standalone theme definitions
   5. Run all Gradle-based checks
   6. Run all IJ tests (e.g., via the `tests.cmd` script)
   7. Verify everything works in the Jewel Standalone sample (components, Markdown rendering)
   8. Verify everything works in the Jewel IDE samples (toolwindow, component showcase)
   9. Verify that the publishing works locally (including POMs, especially for newly added/changed modules — see below)
   10. Verify that the Metalava signatures are matching the ones on `master`
   11. Open a merge request for each cherry-pick branch on Space

## After cutoff

1. After the cherry-picks are merged and the release is cut, bump `jewel.release.version` to the next development
   version, run the version updater, and generate the new API dumps for ongoing development. These stable and
   experimental dumps become the compatibility baseline for all future changes. From this point on, every intentional
   API change must update the dumps for GitHub Actions to pass successfully.
   ```shell
   ../scripts/jewel-version-updater.main.kts
   ../scripts/metalava-signatures.main.kts update
   ```
2. Bump the IJP target to the latest stable in the [Gradle version catalog](../gradle/libs.versions.toml)
3. Reset the Metalava baselines for the new development cycle:
   ```shell
   ../scripts/metalava-signatures.main.kts clean-baselines
   ```
4. When both MRs are approved and merged:
   1. Run the TeamCity job to publish the artefacts to Maven Central
   2. Tag the commits the releases were cut from, with this format: `JEWEL-[Jewel version]-[major IJP version]`. For
      example, for Jewel 0.30.0, `JEWEL-0.30.0-253` on the 251 branch and `JEWEL-0.30.0-261` on the 252 branch.

## Testing publishing locally

Before pulling the trigger on a release process, it's a good idea to make sure that all and only the artefacts that
should be published actually get published. You should also inspect the POM files to make sure that all the dependencies
are set up correctly.

The version of the artefacts is a concatenation of:
 * The Jewel API version as set in [`gradle.properties`](../gradle.properties) — e.g., 0.35.0
 * A hyphen
 * The IJP build, which is composed of the major IJP version, a dot, and the build number
   * For local builds, the latter will be set to `SNAPSHOT`

To do a test publish run, you can run the command
```shell
../scripts/publishJewelStandaloneToMavenLocal.cmd [<platform-build-number>]
```
This will publish Jewel Standalone to the local Maven repository (`~/.m2/repository`). The build number is optional:
when you omit it, the script reads it from [`build.txt`](../../../build.txt) in the project root, e.g., `263.SNAPSHOT`.

The script builds the `//build:jewel_standalone_maven_artifacts` Bazel target. That compiles the Jewel modules and
generates the artefacts into `out/idea-ce/artifacts/maven-artifacts`, then copies them into `~/.m2/repository`. You can
inspect the POMs in either place, but keep in mind the `out/` copy is wiped and regenerated on every run.

Generation runs with validation on, so [`JewelMavenArtifacts.validate`](../../../build/src/org/jetbrains/intellij/build/JewelMavenArtifacts.kt)
fails the build when an artefact is missing, has an unexpected `groupId`, or publishes a POM containing the IntelliJ
Platform fork of coroutines (`org.jetbrains.intellij.deps.kotlinx`). The fork shares the `kotlinx.coroutines.*` packages
with the stock artefact but has a different `groupId`, so Gradle and Maven cannot conflict-resolve the two and consumers
end up with both jars.

> [!NOTE]
> The script tolerates exit code 1. A JVM shutdown hook in the build framework can throw `CancellationException` after
> the build has already succeeded, so only exit codes of 2 or higher are real failures.
