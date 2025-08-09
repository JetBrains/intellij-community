# Fast PR Guide

To get your pull requests merged with any further complications in the community repository, it is advised to follow
these steps:

1. Make sure your commit message first line starts with `[JEWEL-<Jewel YouTrack Id>] `, and squash all the changes in a
   single commit
2. Prefix the PR title with `[JEWEL-<Jewel YouTrack Id>] `, add the `jewel` label, assign yourself and pick at least two
   reviewers from the Jewel team
3. Avoid creating pull requests from chains of feature branches (branching from feature branches) if at all possible, as
   this can create issues when cherry-picking for a release
   * If this can't be avoided, open the PR as a draft
   * Only move it to ready for review when the previous PR in the chain has been merged
   * Make sure your PR is rebased on master before moving it to ready for review
4. Before pushing, run all the GitHub CI checks locally and fix any issues that are found
5. Ensure impacted public APIs are properly covered by KDocs and the docs are up to date
6. Run the `ApiCheckTest` to make sure the API dumps are up to date
7. Ensure there are no breaking changes in stable APIs (they look like removed lines in `api-dump.txt` files), and try
   to avoid or at least minimise changes in experimental APIs too (see `api-dump-experimental.txt` files)

If your change includes changes to the module structure and/or to dependencies, refer to the following sections as well
for more guidance.

If you follow all these instructions correctly, the merge can happen automatically in most circumstances, given the
necessary approvals are in place. Try to avoid circumstances in which a manual merge is required, as that is
a time-consuming bottleneck.

That said, there are circumstances in which a manual merge may still be necessary:
1. Compose upgrades (see [this guide](upgrade-compose.md))
2. Changes that make some tests or checks fail in the monorepo and are not visible until we try to merge

When a manual merge is necessary, someone from the JetBrains side of the Jewel team will take on the task. Usually, that
is Jakub, but others can help if he is away or there is urgency.

## Adding modules

There are multiple tests ensuring the quality of the Community project. Changes to the module structure may trip these
tests during safe-push if not done correctly, and prevent automated merging.

These rules should be followed in addition to the rules above:

1. Ensure that `.idea/modules.xml` is sorted alphabetically
2. Source roots declared in the module `.iml` should match the folder structure. Do not add source roots that don't exist
   and vice versa
3. Place a file called `<module name>.xml` at the root of the `resources` folder for the module
   * The file contains the v2 Plugin configuration for the module
   * For Jewel modules, it is essential to at least declare the dependencies on other modules in this file
   * Use similar existing files as reference, as the format is sadly still undocumented as of the time of writing
4. Avoid manually editing `iml` files and revert all spurious changes that may crop up
5. Run the [`jpsModelToBazelCommunityOnly.cmd`](../../../build/jpsModelToBazelCommunityOnly.cmd) script to update the
   Bazel project structure
   * Double-check all changes it makes and revert unrelated/unnecessary ones
6. Ensure the new module is also present and correctly set up in the Gradle build
7. Ensure the Jewel publishing logic is aware of the new module and its dependencies and that publishing locally creates
   a well-formed POM file
   * See [this guide](RELEASING.md) for more details

## Adding external dependencies

There are multiple tests ensuring the quality of the Community project. Changes to the dependencies may trip these tests
during safe-push if not done correctly, and prevent automated merging.

1. New module dependencies have to be included in the list of 3p libraries with their licenses, which is stored in
   [`CommunityLibraryLicenses.kt`](../../build-scripts/src/org/jetbrains/intellij/build/CommunityLibraryLicenses.kt).
   * If you're adding a new artefact from an existing library, as a separate JPS library, you need to add it to the
     existing library entry, using the `additionalLibraryNames()` API
   * For example, we already have an entry for Compose Multiplatform; if we need a new dependency that's not part of the
     existing JPS entry for Compose, we need to add the new JPS library entry to the `additionalLibraryNames` list
2. Alternatively, a library used by multiple modules can be added to the `libraries` list as a separate module,
   containing only the dependency itself
   * Skiko and Compose Foundation Desktop are such examples
3. Carefully consider the use of the "exported" flag
   * If the flag is off, the dependency is not exposed as a transitive dependency of the module (like Gradle's
     `implementation()`)
   * If the flag is on, the dependency will be exposed as a transitive dependency (like Gradle's `api()`)
   * If libraries expose any non-IJP API, you need to run the `ApiCheckTest`
4. Make sure that dependencies already contained in the platform are not included as transitive dependencies
   * The same applies if the current module depends on another module with this dependency already added
5. Consider the right scope to add the dependency with, and use the narrowest possible one that works
   * [This page](https://www.jetbrains.com/help/idea/working-with-module-dependencies.html#z4mc5ow_51) documents the JPS
     scopes and how they work
   * They roughly map to Gradle scopes as follows:

     | JPS scope                | Gradle scope         |
     |--------------------------|----------------------|
     | `compile` (not exported) | `implementation`     |
     | `compile` (exported)     | `api`                |
     | `test` (not exported)    | `testImplementation` |
     | `test` (exported)        | `testApi`            |
     | `runtime`                | `runtimeOnly`        |
     | `provided`               | `compileOnly`        |
6. Add the dependency to the Gradle build, too, and run the Gradle build to validate it's set up correctly
7. Run the [`jpsModelToBazelCommunityOnly.cmd`](../../../build/jpsModelToBazelCommunityOnly.cmd) script to update the
   Bazel project structure
   * Double-check all changes it makes and revert unrelated/unnecessary ones
8. Ensure the Jewel publishing logic is aware of the new module and its dependencies and that publishing locally creates
   a well-formed POM file
   * See [this guide](RELEASING.md) for more details
