# Releasing new versions of Jewel

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
   * If there are "fake" issues, such as APIs that have been hidden but not removed, or former deprecated APIs that were
     removed by the IJP deprecation removal processes, you can generate new baselines:
     ```shell
     ./scripts/metalava-signatures.main.kts validate --release <previous-release> --update-baselines
     ```
   * Please make **absolutely sure** that all and every issue you're adding to the baselines does not cause breakages!
   * If you're hiding-via-deprecation an API, it maintains binary compatibility but might break source compatibility
     * Make sure all hidden APIs have a proper message; if they're only deprecated and not hidden, ensure they have a
       `ReplaceWith` argument, and test that it works (very important!)
     * If you're in doubt whether to just deprecate or hide an API, prioritise hiding to ensure a pleasant UX at usage
       site, but only if it does not break source compat in the majority of cases
     * Maintaining source compatibility at least when named arguments are used in client code is important and should
       always be done, unless there are specific concerns
     * When not using named arguments in client code, source compatibility is not mandatory — and is often not possible
6. Generate the new Metalava signatures for the new release:
   ```shell
   ./scripts/metalava-signatures.main.kts update --release <new-release>
   ```
7. Write the release notes for the release — use the script to get the draft:
   ```shell
   ./scripts/extract-release-notes.main.kts --since [yyyy-mm-dd]
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
8. Commit all changes and get them merged to `master`
9. Cherry-pick the changes to the target release branches (e.g., `252`)
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
10. When both MRs are approved and merged:
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
