# Icon Resources

## Generated Icon Classes

- Icons under resource roots are normally represented by generated `*Icons.java` classes.
- Do not add local `IconLoader.getIcon(...)` wrappers for icons that should be in a generated icon class. Use the generated field directly from production code and tests.
- If an icon should intentionally be excluded from class generation, add or update an `icon-robots.txt` rule near the icon resources instead of creating an ad hoc loader.
- Generated classes include cache keys derived from normalized SVG content. Do not guess cache keys.

## How To Update

1. Prefer running the IDE run configuration `Icons processing | Generate icon classes (ex icons.gant)` when available.
2. If the run configuration is unavailable, run the icon-class test and apply the exact generated diff it prints for the affected class:
   `bt.cmd --module intellij.platform.images.build.tests.smoke --filter org.jetbrains.intellij.build.images.CommunityIconClassesTest`
3. After applying the generated output, rerun the same test to confirm there are no stale generated icon classes.
4. Also run the affected module tests or compilation for production call-site changes; the icon-class test validates generated files but does not necessarily compile every icon consumer.
5. A new or renamed field in a generated icon class changes the public API. Update the API dumps of each module with a changed icon class. In the monorepo, use the `api-dump` skill to run `ApiCheckTest`. For `AllIcons`, update these modules:
   - `intellij.platform.util.ui` for `AllIcons`
   - `intellij.platform.icons` for `AllIconDescriptors`
   - `intellij.platform.jewel.ui` for `AllIconsKeys`

## Bazel Status

The package `@community//platform/build-scripts/icons` declares the binary target `generate_icon_classes` for the icon class generator. No document gives its run command. Prefer the target over the IDE run configuration once a document does.

Use `bt.cmd` for verification.
