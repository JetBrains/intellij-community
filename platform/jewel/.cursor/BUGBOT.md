Particular areas to validate when it comes to Jewel code and Jewel-adjacent changes:
* Is there any breaking change?
  * Binary compat is a non-negotiable.
  * Source compat is always nice to have, but not at the cost of maintaining an infinite amount of overloads.
  * Binary compat can be obtained by leaving the old version of an API as `DeprecationLevel.HIDDEN`.
  * Experimental APIs should never break binary compat unless there is no reasonable exception.
  * Internal APIs offer no stability guarantee whatsoever.
* Is there any logic issue?
  * If there is any layout logic change in a component, is the layout logic fine?
  * Is it hardcoding things like `fillMaxSize`, which should be left to the user? Components should have a default min size when it makes sense to, and use the `modifier` parameter to be told how big to be by the user.
  * Can you identify if the logic is making any implicit or unreasonable assumption?
* Is the code changing component styling?
  * If so, does the bridge part of the styling use `JBUI` as source for colors/metrics values? If not, does it at least use LaF keys?
  * Check what the IJPL Swing equivalent components do: do they hardcode values? If they do, we can hardcode those values too, but if they are public constants, we should refer to those instead in the bridge.
  * Hardcoding is only acceptable as a last resort in the bridge; it is tolerated in standalone if a value is not available in the global colors/metrics, or in the palette (we don't have `JBUI` or the same LaF keys, nor IJPL Swing components/UIs available in standalone).
* Is all the new/changed public API properly documented with KDocs? Is it up to date? Anything missing or unclear?
* Are we upholding the promise of identical APIs between bridge and standalone (except at the top entry level, e.g. the theme itself)?
* Are we avoiding leaking IJPL or standalone concerns in "common code"?
  * Do we implement common interfaces properly on both sides?
  * If not, is that properly documented/explained and is there an issue on YouTrack for the follow-up with a corresponding TODO in the code?
* Validate testing coverage.
  * If there is something we can test with unit tests or Compose UI tests in the PR, is it well covered? Any missing cases?
  * If this is a bug fix, are we adding adequate regression tests?
  * If this is a new component, is its behavior sufficiently covered?
* Does the PR description use the appropriate release notes template for user-visible changes?
  * "User" in this context means the software engineer using Jewel to build something — not necessarily the end user.
  * See `platform/jewel/docs/pr-guide.md` for the template.
  * Does the release notes section include internal implementation details? They should focus on the user value of a change/fix.
  * API and behavior changes, new functionality, and new features must be covered.
  * Look at `platform/jewel/RELEASE NOTES.md` for a style reference and `platform/jewel/scripts/extract-release-notes.main.kts` for the extraction process.
