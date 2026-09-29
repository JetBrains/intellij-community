# Bazel Specific Tips

You might encounter some quirks in the Bazel built IDE compared to the JPS one. That's to be expected, given that
JetBrains is still refining their build process on Bazel and some parts might still be missing. This doc aims to help
you, dear developer, to ease some of the pains.

## How to open the IJC repo with Bazel

Just download and enable the [Bazel plugin](https://plugins.jetbrains.com/plugin/22977-bazel). A popup should appear
in the bottom right corner asking if you'd like to reload the project using Bazel. Just agree and voilà, Bazel will
be the primary build tool.

## Running ApiCheckTest

You might not see the option the first time you open the IJC repo with Bazel in the "Run Configurations" tool window.
However, a [Bazel target that runs ApiCheckTest](../../../platform/testFramework/monorepo/BUILD.bazel) is available in
the code base, called `monorepo-tests_test`.

### Bazel Way

If you want to run it via Bazel, there are two ways of doing so:

1. Run the target in the IDE directly. Just open the BUILD file and click on the green play button next to `jps_test`.
2. Run the target in the terminal: `./bazel.cmd test //platform/testFramework/monorepo:monorepo-tests_test`

### Old School Way

If you'd rather run the class directly, just open [ApiCheckTest](../../../platform/testFramework/monorepo/tests/api/ApiCheckTest.kt)
 and click on the green play button.

The visual diff dialog will appear in both ways of running the test, just find the `<Click to see difference>` link in
the build output and that's it, the rest from now on will be the same as in JPS.

### Automatically Update API dumps (Bazel way only)

If you trust the API dump code enough, you can change the test target slightly to automatically write the expected
output in the necessary files. Just apply the git patch below, but **remember to not push this code in your branch**!

```git
Subject: [PATCH] automatically update dump files
---
Index: platform/testFramework/monorepo/BUILD.bazel
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
diff --git a/platform/testFramework/monorepo/BUILD.bazel b/platform/testFramework/monorepo/BUILD.bazel
--- a/platform/testFramework/monorepo/BUILD.bazel	(revision 65d9fd06454a6c1ff4925723fc5f944dbd7eda60)
+++ b/platform/testFramework/monorepo/BUILD.bazel	(date 1782307657152)
@@ -9,7 +9,8 @@
     name = "monorepo-tests_test",
     data = ALL_COMMUNITY_TARGETS + [BAZEL_TARGETS_JSON],
     jvm_flags = [
-        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % BAZEL_TARGETS_JSON,
+        "-Dintellij.build.bazel.targets.json.file=$(rlocationpath %s)" % BAZEL_TARGETS_JSON_COMMUNITY,
+        "-Dapi.dump.test.update.files=true",
     ],
     runtime_deps = [":monorepo-tests_test_lib"],
 )
```

But... Please double-check the dumps it changed before pushing.

## Running the Spectre headful UI tests

[Spectre](https://spectre.sebastiano.dev) drives a real Compose Desktop window, so its tests need a display and a
non-headless JVM. That rules out `jps_test`, which forces `-Djava.awt.headless=true` and puts the IntelliJ test
runtime on the classpath — the latter would hide exactly the standalone-runtime leaks these tests exist to catch.
The lane therefore uses the `spectre_test` macro in [spectre.bzl](../spectre.bzl), which runs the JUnit Platform
console launcher on a classpath containing only the module under test, Compose, and Spectre.

These are ordinary test targets. On any machine with a display they run as part of `bazel test //platform/jewel/...`,
or on their own:

```bash
./bazel.cmd test //platform/jewel/int-ui/int-ui-standalone-tests:jewel-intUi-standalone-spectre-tests
```

Windows will open and close on your desktop while they run; synthetic input goes to the test window, so you can keep
working, but do not be surprised by the flicker.

Every Spectre target is tagged `requires-display`. That tag is both the CI routing hint and the escape hatch: a lane
that genuinely has no display skips them with `--test_tag_filters=-requires-display`.

### Scope: standalone only

This lane covers **standalone** Jewel, and cannot be extended to the IJP bridge. Spectre automates a Compose Desktop
window; inside the IDE a Jewel popup is a `JBPopup` hosting a `ComposePanel`, and the application under test is the
IDE itself. So `JBPopupRenderer` and the other bridge renderers currently have **no headful coverage at all** — only
the Compose UI unit tests in `ui-tests`. That gap is JEWEL-1397, which is IDE Starter / UI Driver work rather than
Spectre work. Do not try to add a bridge test to `src/spectreTest`; it cannot run there.

### Adding a Spectre test

Put the class under a `src/spectreTest/kotlin` source root, in a package under `org.jetbrains.jewel`, and you are
done — there is no list of test classes to keep in sync.

Discovery is by package (`--select-package`), which JUnit resolves through the class loader. Both obvious
alternatives are broken under Bazel, and each fails silently rather than loudly:

- a bare `--scan-classpath` scans only classpath *directories*, and under Bazel every classpath entry is a jar;
- `--scan-classpath=<jar path>` assumes a runfiles symlink tree, so it finds nothing on Windows, where Bazel uses a
  runfiles manifest instead.

That is why the macro also passes `--fail-if-no-tests`: it turns any future variant of that mistake into a red lane
instead of a green one that ran nothing.

### CI

Spectre targets are meant to run on **both** CI systems, and neither needs a target list — both select by tag, so a
new `spectre_test` target anywhere under `platform/jewel` joins CI on its own:

```bash
bazel test //platform/jewel/... --build_tests_only --test_tag_filters=requires-display
```

**GitHub Actions** runs two jobs from [jewel-checks.yml](../../../.github/workflows/jewel-checks.yml): `Jewel Spectre
tests` on Linux under Xvfb, and `Jewel Spectre tests (Windows)`, which needs no display setup because Windows always
hands the process a window station.

**TeamCity / Patronus** should run them alongside the headful IJP tests, on display-capable agents. That
configuration lives internally and there is nothing in this repository that drives it — the command above is the
whole contract, and `requires-display` is the tag to route on. A headless agent that picks up `//platform/jewel/...`
must exclude them with `--test_tag_filters=-requires-display`, or every Spectre test will fail in `XToolkit`'s
static initialiser.

Windows is not redundant coverage: `JDialogRenderer` renders popups through `RenderSettings.SwingGraphics` there
rather than Compose's default, with a different transparency hack, so it is a genuinely different code path.

Spectre itself is declared in [jewel_deps.MODULE.bazel](../jewel_deps.MODULE.bazel) and exposed as the `testonly`
target `//platform/jewel:spectre`. Keep it that way: `testonly` is what makes Bazel refuse to let a production target
depend on it, which is how Spectre stays out of the IDE and out of published Jewel artifacts.

## Add Devkit to IDE Build

On JPS we had a handy run configuration that built the IDE with the devkit bundled. On Bazel, the `idea_community`
launcher does not bundle it.

This means that you won't find anything if you try to find the Jewel Components Showcase or Jewel Tool Window via
the actions search.

Run the launcher that adds the devkit instead:

```shell
bazel run //build:idea_community_devkit
```

`idea_community_devkit` is the launcher of the `IDEA With Compose (dev build)` run configuration. It adds the
`intellij.devkit` plugin to IDEA Community.

A new launcher is a `DevMainKt` run configuration in `community/.idea/runConfigurations` plus a generator run from the
ultimate root. A change of `IdeaCommunityProperties` reaches a launcher only after a generator run, because the
launchers read the generated distribution declarations under `build/`.
