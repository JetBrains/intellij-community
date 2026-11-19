# Library Version Changes

Bump a library with the dashboard tool, not by hand: `bun community/build/libraries-dashboard/libraries-dashboard.mjs bump <groupId:artifactId>=<version> --kind=wrapper`. It rewrites the `maven-id`, the jar URLs and every `<sha256sum>`, and prints the follow-up commands.

## The artifact list must match the POM

JPS resolves a repository library from its POM and requires the `<verification>` artifact set to equal the resolved jar set. Bazel downloads the listed URLs instead, so a mismatch passes every local Bazel build and fails the first JPS step on TeamCity ("Library 'x': not found expected artifacts"). Two rules follow:

- A transitive artifact in a library block stays at the version the POM of the main artifact declares. Do not align it with another wrapper by editing the version.
- To share one version across wrappers, exclude the dependency and add a module dependency on its wrapper. `community/libraries/mockk/jvm/intellij.libraries.mockk.jvm.iml` excludes `byte-buddy-agent` and depends on `intellij.libraries.byte.buddy.agent`.

The bump command checks a multi-artifact library against the direct dependencies of the new POM and fails on a mismatch. After a manual edit of a library block, run the same check:

```bash
bun community/build/libraries-dashboard/libraries-dashboard.mjs check <groupId:artifactId>
```

The check reads one POM without its parents and compares direct dependencies only. To reproduce the JPS resolution that TeamCity runs before every build, run `./build/downloadLibraries.cmd`. It needs Space credentials.

## Version copies outside the JPS model

After you change the version of a repository library in an `*.iml` or in `.idea/libraries/*.xml`, run the Fleet generator in the monorepo checkout:

```bash
./fleet/build/generateProjectModel.cmd dump
```

The Fleet generator copies the JPS library versions into `fleet/build/jps-library-mappings.tsv` and both `fleet/kmp.MODULE.bazel` files. Its `check` mode fails on drift.

When the dump changes a `kmp.MODULE.bazel`, update both Bazel lockfiles:

```bash
./bazel.cmd mod deps --lockfile_mode=update
(cd community && ./bazel.cmd mod deps --lockfile_mode=update)
```

The `kmp` module extension records its artifact list in the `MODULE.bazel.lock` of each module, and CI runs Bazel with `--lockfile_mode=error`, so a stale lockfile fails the build.

Some files outside the JPS model copy a library version, and the project structure tests check each copy:

- `community/platform/jps-bootstrap/pom.xml` copies every library that jps-bootstrap uses (`JpsBoostrapStructureTest`).
- `JetBrainsAnnotationsExternalLibraryResolver.VERSION` copies `org.jetbrains:annotations` (`IdeaUltimateProjectStructureTest`).

The bump command rewrites these copies and reports a copy that still differs. After a manual version change, edit them by hand. Then run the tests that gate the Smoke Tests build:

```bash
./tests.cmd --module intellij.projectStructureTests --test 'com.intellij.ideaProjectStructure.fast.*'
```

These tests also check the Kotlin, Compose and LanguageTool version alignment and the unused project libraries.
