# Releasing new versions of Jewel

> [!NOTE]
> There is no detailed documentation for the process right now.

Releasing a new Jewel version is a complex process that involves cherry-picking changes from the master branch to the
target release branches, and it can only be done by someone at JetBrains with access to the monorepo. After the
cherry-picks are done and merged to the respective branches, they need to trigger the TeamCity job to publish the
artefacts to Maven Central.

Please ping Jakub, Nebojsa, or Sasha for help and guidance.

High-level steps:

1. Bump the Jewel API version in [`gradle.properties`](../gradle.properties) and the IJP target to the latest stable
   in the [Gradle version catalog](../gradle/libs.versions.toml)
2. Run the [Jewel version updater script](../scripts/jewel-version-updater.main.kts), then ktfmt
3. Run the Gradle checks to make sure everything is ok:
   ```shell
   ./gradlew check detekt detektMain detektTest --continue
   ```
4. In all Metalava baseline files (`metalava/{moduleName}-baseline[-stable]-current.txt`), remove all findings, only
   leaving the baseline version header on the first line. This lets you see all issues accumulated over this release in
   the following steps
5. Run the Metalava validator against the previous release on the `master` branch, and fix any issues you find:
   ```shell
   ./scripts/metalava-signatures.main.kts validate --release <previous-release>
   ```
6. Generate the new Metalava signatures for the new release:
   ```shell
   ./scripts/metalava-signatures.main.kts update --release <new-release>
   ```
7. Commit all changes and get them merged to `master`
8. Cherry-pick the changes to the target release branches (e.g., `252`)
   1. Make sure you've not included IJP major release-specific changes
   2. Update the Kotlin version in the [Gradle version catalog](../gradle/libs.versions.toml) to match the IJ Platform's Kotlin version
   3. Update other related versions if needed
   4. Run `./gradlew generateThemes --rerun-tasks` to update the standalone theme definitions
   5. Run all Gradle-based checks
   6. Run all IJ tests (e.g., via the `tests.cmd` script)
   7. Verify everything works in the Jewel Standalone sample (components, Markdown rendering)
   8. Verify everything works in the Jewel IDE samples (toolwindow, component showcase)
   9. Verify that the publishing works locally (including POMs, especially for newly added/changed modules — see below)
   10. Verify that the Metalava signatures are matching the ones on `master`:
       ```shell
       ./scripts/metalava-signatures.sh --validate --release <new-release>
       ```
   11. Open a merge request for each cherry-pick branch on Space
9. When both MRs are approved and merged:
   1. Run the TeamCity job to publish the artefacts to Maven Central
   2. Tag the commits the releases were cut from, with this format: `JEWEL-[Jewel version]-[major IJP version]`. For
      example, for Jewel 0.30.0, `JEWEL-0.30.0-251` on the 251 branch and `JEWEL-0.30.0-252` on the 252 branch.

## Testing publishing locally

Before pulling the trigger on a release process, it's a good idea to make sure that all and only the artefacts that
should be published actually get published. You should also inspect the POM files to make sure that all the dependencies
are set up correctly.

The version of the artefacts is a concatenation of:
 * The Jewel API version as set in [`gradle.properties`](../gradle.properties) — e.g., 0.29.0
 * A hyphen
 * The IJP build, which is composed of the major IJP version, a dot, and the build number
   * For local builds, the latter will be set to `SNAPSHOT`

To do a test publish run, you can run the
[`JewelMavenArtifactsBuildTarget`](../../../build/src/JewelMavenArtifactsBuildTarget.kt) task. The artefacts will be
written to the [`maven-artifacts`](../../../out/idea-ce/artifacts/maven-artifacts) folder.

> [!IMPORTANT]
> If you are running it from the Community repository, you'll need to temporarily change the value of the
> `BuildContextImpl.createContext`'s `projectHome` from its default value of `ULTIMATE_HOME` to
> `COMMUNITY_ROOT.communityRoot`.
>
> Don't commit the change!
