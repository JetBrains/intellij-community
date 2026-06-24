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

## Add Devkit to IDE Build

On JPS we had a handy run configuration that built the IDE with the devkit bundled. On Bazel, the `idea_community`
configuration does not do that by default.

This means that you won't find anything if you try to find the Jewel Components Showcase or Jewel Tool Window via
the actions search.

No biggie, we can circumvent this easily enough by just applying **one** of
the git patches below. Choose the one that you think best suits your tastes:

### Add a new build target

```git
Subject: [PATCH] add ijc build with devkit
---
Index: build/BUILD.bazel
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
diff --git a/build/BUILD.bazel b/build/BUILD.bazel
--- a/build/BUILD.bazel	(revision 65d9fd06454a6c1ff4925723fc5f944dbd7eda60)
+++ b/build/BUILD.bazel	(date 1782307889085)
@@ -73,6 +73,12 @@
     runtime_deps = [":build"],
 )

+intellij_dev_binary_community(
+    name = "idea_community_devkit",
+    platform_prefix = "community",
+    additional_modules = "intellij.devkit",
+)
+
 # Dev-build targets for running IDEs locally via `bazel run //build:<target>`
 intellij_dev_binary_community(
     name = "idea_community",
```

And then run with: `bazel run //build:idea_community_devkit`

Once you run the command once, the configuration will be automatically "saved" in the configurations list.

### Add the Devkit module to the existing IJC build

Apply the git patch below:

```git
Subject: [PATCH] modify current idea_community build to bundle devkit
---
Index: build/BUILD.bazel
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
diff --git a/build/BUILD.bazel b/build/BUILD.bazel
--- a/build/BUILD.bazel	(revision 65d9fd06454a6c1ff4925723fc5f944dbd7eda60)
+++ b/build/BUILD.bazel	(date 1782308040029)
@@ -77,6 +77,7 @@
 intellij_dev_binary_community(
     name = "idea_community",
     platform_prefix = "community",
+    additional_modules = "intellij.devkit",
 )

 intellij_dev_binary_community(

```

Run with: `bazel run //build:idea_community` **or** just run the idea_community configuration in the configurations
list.

### Forcing IdeaCommunityProperties to bundle Devkit

Apply the git patch below:

```git
Subject: [PATCH] add devkit as a bundled plugin in IdeaCommunityProperties
---
Index: build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt
IDEA additional info:
Subsystem: com.intellij.openapi.diff.impl.patch.CharsetEP
<+>UTF-8
===================================================================
diff --git a/build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt b/build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt
--- a/build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt	(revision 65d9fd06454a6c1ff4925723fc5f944dbd7eda60)
+++ b/build/src/org/jetbrains/intellij/build/IdeaCommunityProperties.kt	(date 1782308311182)
@@ -59,7 +59,7 @@
     )

     productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf(
-      "intellij.javaFX.community"
+      "intellij.javaFX.community", "intellij.devkit"
     )

     productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
```

Run with: `bazel run //build:idea_community` **or** just run the idea_community configuration in the configurations
list.

Whatever you choose, just remember to not push these changes to your branch :)
