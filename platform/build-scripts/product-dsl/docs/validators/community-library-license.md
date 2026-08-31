# Community Library License Validation

Entry point: `CommunityLibraryLicenseValidator` (`NodeIds.COMMUNITY_LIBRARY_LICENSE_VALIDATION`).

## Overview

A community product reads `CommunityLibraryLicenses.LICENSES_LIST` alone. `ProductProperties.allLibraryLicenses`
defaults to it, and `IdeaCommunityProperties` and `PyCharmCommunityProperties` never override that default. An
ultimate product overrides it with `UltimateLibraryLicenses.LICENSES_LIST`, which **holds the community list**
through its own concatenation.

[library-license.md](library-license.md) reads `libraryLicenses`, and the ultimate generator fills that field with
the ultimate superset. So an entry that sits in the ultimate-only part covers a library for that rule, while the
community build still finds no license. `IdeaCommunityBuildTest.build(third_party_libraries)` then fails.

This rule closes that gap. It reads the community list alone.

The rule was measured against a deliberate regression, so it is known to fail. Read
[Measured report](#measured-report).

## Inputs

- Config: `communityLibraryLicenses` on `ModuleSetGenerationConfig`. Pass the community list, never the superset.
- Discovery: `DiscoveryResult.communityShippedModuleSets`, which is the COMMUNITY and CORE labels together.
- JPS model: the module of each name through `ModuleOutputProvider.findModule`.
- JPS model: production runtime dependencies, through the shared `collectMissingLicenseViolations` helper.

## Why those two labels

`ultimateGenerator` maps the COMMUNITY and CORE labels to the community generated META-INF directory, and the
ULTIMATE label to the `licenseCommon` one. A module set of either of the first two labels therefore describes
community content, and a library it ships needs a community entry.

A COMMUNITY label does not imply a community source tree. `CommunityModuleSets.rdCommon()` names modules under
`remote-dev/`. The label still implies community shipping, because `IdeaCommunityProperties` bundles that set.

## Rules

- Walk every module of every set in `communityShippedModuleSets`, nested sets included, through `visitAllModules`.
- Skip a name that no JPS module matches.
- Read the production runtime libraries of each module, recursively.
- Key each library by `getLibraryFileName`.
- Skip a library that the implicit sub-library rule covers, and skip the library `ant`. Both come from the shared
  helper, so the two rules agree.
- Report a library that has no entry in `communityLibraryLicenses`.
- An empty license list turns the rule off.
- A non-empty license list with no module in scope is a failure.

## Suppression and allowlists

- No suppression and no allowlist are supported. The triage below found no entry that needs one.

## Output

- `MissingLibraryLicenseError` with the category `MISSING_LIBRARY_LICENSE`, the context
  `the community and core module sets`, and the rule name `CommunityLibraryLicenseValidation`.
- The error carries `licenseFile`, so the fix line names `CommunityLibraryLicenses.kt` alone. The two rules share
  the error class, and the `Scope` line separates the reports.

## Auto-fix

- No. The fix is to move the entry from `UltimateLibraryLicenses.kt` to `CommunityLibraryLicenses.kt`. Move it,
  never copy it: `IdeaUltimateLibraryLicensesTest` forbids a duplicate and enforces the sort order.

## Measured report

With the three entries below in `UltimateLibraryLicenses.kt`, the rule names all three. With each one moved to
`CommunityLibraryLicenses.kt`, it names nothing.

| Library | Module | Module set |
|---|---|---|
| `google.protobuf.java.util` | `intellij.libraries.protobuf.java.util` | `CoreModuleSets.librariesPlatform()` |
| `SSHJ` | `intellij.libraries.sshj` | `CoreModuleSets.librariesIde()` |
| `jetbrains.patronus.codeowners.lib.ownership` | `intellij.platform.testFramework.junit5.eel.tests` | `CommunityModuleSets.platformTestFrameworksCore()` |

Only the protobuf entry broke a build. The other two carry a JetBrains own group id, and
`LibraryLicensesListGeneratorJps` drops such a library through `LibraryLicense.isJetBrainsOwnLibrary`. This rule
applies no such filter, which is why it saw them.

## Known limits

- **The label is a proxy for community shipping.** The precise input would be the products that the community
  repository builds. `DiscoveredProduct` carries no such flag, so a product-based rule needs a new config field and
  a product list. A COMMUNITY or CORE set that only an ultimate product uses would produce a false positive. No such
  set exists today.
- **A test framework module set counts as shipped.** `CommunityModuleSets.platformTestFrameworksCore()` holds test
  framework modules, and the rule reads them. That is what the label says. Whether a community product really
  packages such a jar is a separate question.

## Non-goals

- The Maven descriptor filter and the JetBrains group filter of the `third_party_libraries` build step. The sibling
  rule states the same non-goal, and this rule follows it.
- The ultimate side. [library-license.md](library-license.md) covers every library of the graph against the
  superset.
- Validation of the license text or the license identifier in an entry.

## Related

- [library-license.md](library-license.md)
- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
