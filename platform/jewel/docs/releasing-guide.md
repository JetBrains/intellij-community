# Releasing new versions of Jewel

> [!NOTE]
> There is no detailed documentation for the process right now.

Releasing a new Jewel version is a complex process that involves cherry-picking changes from the master branch to the
target release branches, and it can only be done by someone at JetBrains with access to the monorepo. After the
cherry-picks are done and merged to the respective branches, they need to trigger the TeamCity job to publish the
artefacts to Maven Central.

Please ping Jakub, Nebojsa, or Sasha for help and guidance.

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
