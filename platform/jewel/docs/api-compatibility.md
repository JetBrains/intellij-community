# API Compatibility

Jewel uses Metalava to track API compatibility for every module with API dumps in its `metalava/` directory.

There are two API surfaces:

* Stable API dumps, stored as `metalava/<module>-api-stable-<version>.txt`
* Experimental API dumps, stored as `metalava/<module>-api-<version>.txt`

The current release version is read from [`gradle.properties`](../gradle.properties) via `jewel.release.version`. By
default, the Metalava scripts validate and update dumps for that version.

## Validate API dumps

To validate all Jewel API dumps, run this from the Jewel root:

```shell
./scripts/metalava-signatures.main.kts validate
```

Useful variants:

```shell
./scripts/metalava-signatures.main.kts validate --module ui
./scripts/metalava-signatures.main.kts validate --stable-only
./scripts/metalava-signatures.main.kts validate --experimental-only
./scripts/metalava-signatures.main.kts validate --release 0.35.0
./scripts/metalava-signatures.main.kts validate --update-baseline
```

* `--module ui`: only validate one module
* `--stable-only`: only validate the stable API surface
* `--experimental-only`: only validate the experimental API surface
* `--release 0.35.0`: validate against dumps for a specific release instead of the current `jewel.release.version`
* `--update-baseline`: write Metalava findings to the baseline instead of fixing them immediately

When validation fails because an API dump changed, the failing Metalava task:

* fails the Gradle build, which in turn fails GitHub Actions
* prints the generated API dump patch to stdout
* writes the patch to a file under the module `build/reports/metalava/` directory

If the `diff` executable is unavailable, the task still fails, but no patch can be produced.

## Update API dumps

If an API change is intentional, update the stored API dumps:

```shell
./scripts/metalava-signatures.main.kts update
```

Useful variants:

```shell
./scripts/metalava-signatures.main.kts update --module ui
./scripts/metalava-signatures.main.kts update --stable-only
./scripts/metalava-signatures.main.kts update --experimental-only
./scripts/metalava-signatures.main.kts update --release 0.35.0
```

* `--module ui`: only update the `ui` module
* `--stable-only`: only regenerate the stable API dumps
* `--experimental-only`: only regenerate the experimental API dumps
* `--release 0.35.0`: generate dumps for a specific release instead of the current `jewel.release.version`

For normal development, the rule is simple:

* every intentional API change must be accompanied by updated API dumps
* otherwise GitHub Actions will keep failing on the Metalava validation step

Stable API changes must preserve binary compatibility and should preserve source compatibility where
practical. Experimental API changes must preserve binary compatibility, but may evolve in source-incompatible ways.
Intentional experimental API changes must still update the corresponding API dumps.

If a stable API needs to evolve, do not remove or change the old entry in a way that breaks existing callers. Instead,
keep the previous function or constructor as a compatibility shim, mark it appropriately for migration, and have it
delegate to the new API. A common pattern is to keep the old declaration hidden from normal use while preserving
compatibility for existing binaries and source migrations.

## Compatibility-preserving API evolution

When a stable API changes, prefer adding a new declaration and keeping the old one as a delegating compatibility shim.
The old declaration should be deprecated with `DeprecationLevel.HIDDEN` and should point users to the latest
non-deprecated API.

In practice:

* keep old stable declarations callable for existing binaries
* preserve source compatibility where practical by keeping the previous entry point and delegating to the new one
* use deprecation messages and `ReplaceWith` to point to the latest non-deprecated API
* only update the API dumps after the compatibility-preserving shim is in place

### Example: add a new function

If a new function replaces an older stable one, keep the old one and make it delegate:

```kotlin
public fun showBanner(text: String, accentColor: Color) {
    // New implementation
}

@Deprecated(
    message = "Use showBanner(text, accentColor) instead.",
    replaceWith = ReplaceWith("showBanner(text, accentColor)"),
    level = DeprecationLevel.HIDDEN,
)
public fun showBanner(text: String) {
    showBanner(text, accentColor = DefaultBannerAccentColor)
}
```

### Example: add a new constructor

The same pattern applies to constructors:

```kotlin
public class BannerStyle(
    public val accentColor: Color,
    public val borderColor: Color,
)

@Deprecated(
    message = "Use BannerStyle(accentColor, borderColor) instead.",
    replaceWith = ReplaceWith("BannerStyle(accentColor, borderColor)"),
    level = DeprecationLevel.HIDDEN,
)
public constructor(accentColor: Color) : this(
    accentColor = accentColor,
    borderColor = DefaultBannerBorderColor,
)
```

### Example: add a new parameter

If a new parameter is added to the public surface, add a new declaration with the new signature and make the previous
one delegate by passing an explicit default value for the new parameter:

```kotlin
public fun showBanner(
    text: String,
    accentColor: Color,
    animate: Boolean,
) {
    // New implementation
}

@Deprecated(
    message = "Use showBanner(text, accentColor, animate) instead.",
    replaceWith = ReplaceWith("showBanner(text, accentColor, animate = false)"),
    level = DeprecationLevel.HIDDEN,
)
public fun showBanner(
    text: String,
    accentColor: Color,
) {
    showBanner(
        text = text,
        accentColor = accentColor,
        animate = false,
    )
}
```

The same rule applies to constructors that gain a new parameter: add the new constructor and make the previous one
delegate with an explicit default value for the newly added parameter.

All deprecation messages should point to the latest available non-deprecated API.

If the failure is not an API dump change, but a Metalava finding that should be suppressed instead, update the matching
baseline file.
