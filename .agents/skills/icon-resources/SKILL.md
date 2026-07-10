---
name: icon-resources
description: Work with IntelliJ icon resources, generated *Icons.java classes, icon-robots.txt rules, IconLoader wrappers, or CommunityIconClassesTest failures.
---

# Icon Resources

Use this skill when adding, renaming, moving, deleting, or referencing icons under a Java resource root, or when a failure mentions generated icon classes or `icon-robots.txt`.

## Generated Icon Classes

- Icons under resource roots are normally represented by generated `*Icons.java` classes.
- Do not add local `IconLoader.getIcon(...)` wrappers for icons that should be in a generated icon class. Use the generated field directly from production code and tests.
- If an icon should intentionally be excluded from class generation, add or update an `icon-robots.txt` rule near the icon resources instead of creating an ad hoc loader.
- Generated classes include cache keys derived from normalized SVG content. Do not guess cache keys.

## How To Update

1. Prefer running the IDE run configuration `Icons processing | Generate icon classes` when available.
2. If the run configuration is unavailable, run the icon-class test and apply the exact generated diff it prints for the affected class:
   `./tests.cmd --module intellij.platform.images.build.tests --test org.jetbrains.intellij.build.images.CommunityIconClassesTest`
3. After applying the generated output, rerun the same test to confirm there are no stale generated icon classes.
4. Also run the affected module tests or compilation for production call-site changes; the icon-class test validates generated files but does not necessarily compile every icon consumer.

## Bazel Status

As of this guide, icon class generation is not exposed as a Bazel runnable target. The package `@community//platform/build-scripts/icons` contains library and test targets, but no binary target:

`bazel query 'kind(".*binary.*", @community//platform/build-scripts/icons:all)'`

Use Bazel-backed `tests.cmd` for verification. If a generator binary target is added later, prefer that documented Bazel target over the IDE run configuration.
