---
name: Plugin Loading Problems
description: Implemented behavior for loading explanations, notifications, and recovery in the Plugins page.
targets:
  - ../../../core-api/resources/messages/CoreBundle.properties
  - ../../src/com/intellij/ide/plugins/PluginLoadingProblem.kt
  - ../../src/com/intellij/ide/plugins/PluginLoadingProblemAnalyzer.kt
  - ../../src/com/intellij/ide/plugins/PluginManagerStateService.kt
  - ../../src/com/intellij/ide/plugins/PluginInitializationErrorReporter.kt
  - ../../src/com/intellij/ide/plugins/PluginLoadingProblemsNotificationHandler.kt
  - ../../src/com/intellij/ide/plugins/DefaultPluginLoadingProblemsNotificationHandler.kt
  - ../../src/com/intellij/ide/plugins/UltimateDependencyCheckerImpl.kt
  - ../../src/com/intellij/ide/plugins/marketplace/CheckErrorsResult.kt
  - ../../src/com/intellij/ide/plugins/ListPluginModel.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginRowInput.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginModelAsyncOperationsExecutor.kt
  - ../../src/com/intellij/ide/plugins/newui/DefaultUiPluginManagerController.kt
  - ../../src/com/intellij/ide/plugins/newui/MyPluginModel.kt
  - ../../src/com/intellij/ide/plugins/newui/ErrorComponent.java
  - ../../src/com/intellij/ide/plugins/newui/ListPluginComponent.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginDetailsPageComponent.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginRowFactory.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginDetailsPresenter.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProvider.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProvider.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemPresentationTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemUiTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingDependencyProblemTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProviderTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt
---

# Plugin Loading Problems

Status: Active
Date: 2026-10-16

## Purpose

Describe the implemented loading explanations, notification reporting, and recovery suggestions.

Add or change a requirement in the same changelist as its implementation. Keep future designs outside this specification.

## Scope

This specification covers loading problems in both Plugins pages and startup notifications.

The [UI contract](./unified-plugin-manager-ui.spec.md) owns page layout, search, ordering, and source request failures.
The [operation contract](./plugin-operations.spec.md) owns action execution, Apply, Reset, and installation failures.
Remote development extends these rules with side aggregation and action routing.

### Non-goals

- This specification does not define diagnostic log text or log severity.
- This specification does not define plugin resolution or product feature support.
- This specification does not define Marketplace networking or the Required Plugins feature.

## Problem Selection

- Each local side exposes at most one loading problem per plugin ID.
- A plugin problem supplies a short page explanation, a detailed notification explanation, and notification eligibility.
- Messages use localized templates and the descriptor data available when the consumer reads the message.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`;
    `messages read the current descriptor data`
  )

- A disabled root of a required dependency chain must remain identifiable through intermediate plugins and content modules.
- Ordinary disabled dependencies use their available names. Recovery retains canonical IDs, including when a dependency uses an alias.
- Factory messages use an ID fallback when no descriptor is available.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `transitive disabled dependency reports the disabled root`;
    `several intermediate plugins report one disabled root`;
    `a required content module reports the transitive disabled root`;
    `a core content module reports the disabled root instead of IDEA CORE`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingDependencyProblemTest.kt (
    `a disabled alias displays its owner name and retains the recovery ID`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`;
    `disabled dependency names remain lazy and recovery uses IDs`
  )

- Missing modules retain their terminal explanation through required plugins and modules, including core modules.
- Other chains refer to the first required plugin outside the affected plugin.
- This fallback excludes disabled roots, product mode restrictions, configuration restrictions, missing modules, and visibility failures.

- For a chain within one plugin, analysis uses the terminal exclusion when it has a supported explanation.
- Without a cross-plugin boundary, an unresolved plugin dependency uses the installation message only when no known provider exists.
- Known providers include candidates and selection exclusions for the dependency ID or alias.
- A plugin excluded during selection uses the failed dependency message and its available name.
- Several excluded providers for one ID or alias use the first excluded provider's name and owner ID.
- Dependency explanations retain aliases from superseded bundled plugins when their updates omit those aliases.
- An excluded content module alias uses its plugin owner's name and ID.
- An unresolved content module uses an unavailable module explanation, not a plugin installation message.
- The explanation retains the module ID and namespace. It offers no Enable target.
- An invisible dependency without a cross-plugin boundary reports the inaccessible module and its owner's name.
- The problem retains the module ID, namespace, and owner ID. It offers no Enable target or installation message.
- Visibility failures across plugin boundaries name the plugin whose descriptor or required module has the access problem.
- They must not name an earlier intermediate plugin or the provider solely because it owns the inaccessible module.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingDependencyProblemTest.kt (
    `an absent plugin uses the installation message`;
    `an absent modern plugin dependency uses the installation message`;
    `an incompatible installed dependency uses its name`;
    `an installed dependency excluded by an ID conflict reports a failed plugin`;
    `an incompatible installed alias reports its failed owner`;
    `an ambiguous installed alias uses its first excluded owner`;
    `a bundled dependency and its excluded update use the first excluded descriptor`;
    `an alias removed by an update retains its excluded bundled owner`;
    `an excluded content module alias retains its plugin owner`;
    `a broken installed dependency uses its name`;
    `a missing module has an unavailable module explanation`;
    `a missing module retains its explicit namespace`;
    `a required module keeps its missing dependency explanation`;
    `an invisible module reports an access restriction`;
    `a required module reports an internal dependency from another namespace`;
    `an internal dependency from the same namespace does not produce a problem`;
    `a visibility failure in a direct dependency names that dependency`;
    `a visibility failure through several plugins names the plugin with the access problem`;
    `a visibility failure in a required module names its owning plugin`;
    `a failed core module reports its missing module`;
    `a missing module remains the explanation through intermediate plugins`;
    `a required module retains a missing module across a plugin boundary`;
    `a missing plugin across a plugin boundary still names the failed dependency`;
    `a failed module across plugin boundaries retains the first required module`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `module dependency data retains its namespace`
  )

- Page results retain the complete module ID for module dependency problems and product mode restrictions.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `module problems retain their IDs in page results`;
    `product mode exclusions retain their page classification`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemPresentationTest.kt (
    `the page result retains module IDs through serialization`
  )

- Supported compatibility and selection exclusions can also produce plugin explanations.
- For a plugin version listed as broken, the short explanation says `Version is known to be broken`.
- The detailed explanation identifies the plugin and version as known to be broken.
- Declared incompatibility messages call the conflicting owner a plugin, not a module. They retain its plugin ID.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`
  )

- A package-prefix conflict in an optional content module can produce an explanation while the main plugin remains loaded.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `optional module conflicts retain the last problem and a Disable target`
  )

Untested: Focused analysis tests do not cover all terminal mappings.

## Visibility

- A reporting policy that suppresses notifications must retain the plugin problems and their recovery data.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `reporting policy only suppresses notification messages`
  )

- Notification eligibility filters detailed notification text. It does not remove the problem from the plugin problem collection.
- Dependency problems for implementation detail plugins retain their recorded problem without notification text or recovery targets.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `implementation detail errors are retained but not reported to the user`;
    `a hidden disabled dependency problem has no recovery targets`
  )

- A plugin explicitly disabled without another failure must produce no loading problem or suggested action.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `explicitly disabled plugin has no loading error or suggested action`
  )

- Unused on-demand modules produce no loading explanation.
- Direct AI disablement, expired license, and unaccepted privacy exclusions produce no loading explanation from this analysis.
- Direct superseded versions produce no loading explanation.
- Other chains across plugin boundaries follow the dependency selection rule even when their terminal exclusion has no direct explanation.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `a product mode exclusion does not suppress an unrelated missing dependency`
  )

- An unavailable optional module alone must not produce a loading problem for its loaded plugin or dependents.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingDependencyProblemTest.kt (
    `an optional module with a missing dependency does not exclude its owner`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `an optional product mode module does not produce a loading problem`
  )

Untested: Focused tests do not cover every direct exclusion and its transitive dependents.

## Plugins Page

- An ordinary loading problem without a disabled dependency supplies its short explanation to the page.
- Expected problems remain outside the generic red error area.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemPresentationTest.kt (
    `an expected product mode problem stays outside the generic error area`;
    `an expected configuration explanation retains an unexpected error from the other side`
  )
- Expected product mode and configuration exclusions gray the plugin icon and title, as for a disabled plugin. They add no info icon.
- The enable/disable control remains active and retains the user's enabled setting.
- The details pane shows the explanation in gray text in the loading error area.
- The row and its control expose the explanation to screen readers. The details text remains accessible without hover.
- These problems do not change the user's enabled setting or enter error sorting.
- An expected explanation must not hide an unexpected error from the other side.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `product mode exclusions gray the plugin and keep the toggle active on reused rows`;
    `a product mode exclusion keeps an ordinary side error visible`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemUiTest.kt (
    `product mode exclusions gray rows and show details explanations until the mode changes`;
    `custom plugin restrictions use expected presentation until the restriction clears`;
    `subset restrictions use expected presentation until the restriction clears`;
    `core-only restrictions use expected presentation until the restriction clears`;
    `selected details refresh expected explanations without hiding errors`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProviderTest.kt (
    `local hints stay separate from errors and clear with replacement data`;
    `plugins with loading errors appear first in local sections without reordering peers`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt (
    `Marketplace hints stay separate from errors and retain both sides`
  )

- Error text uses the theme's error foreground. Recovery links use the theme's link foreground.
- Rows and details use the same error formatting and dependency suggestion data.

Untested: No focused test covers the complete loading error presentation in rows and details.

- For ordinary disabled dependency problems, the controller offers Enable when the current required targets are disabled, present, and compatible.
- This suggestion must include the disabled root rather than a failed intermediate plugin.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `disabled dependency offers an Enable suggestion`;
    `a transitive disabled dependency offers an Enable suggestion`
  )

- The page must suppress loading problems for an affected plugin that the user disables in the session.
- The page must remove the Enable suggestion when the session dependencies resolve.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `a disabled affected plugin suppresses loading problems`;
    `a disabled affected plugin suppresses transitive loading problems`;
    `resolved session dependencies suppress the Enable suggestion`;
    `resolved transitive session dependencies suppress the Enable suggestion`
  )

- The controller suppresses loading errors for plugins installed or uninstalled without restart in the current installation state.

Untested: No focused loading problem test verifies these installation transitions or missing and incompatible suggestion targets.

## Notifications

- Notification reporting collects detailed messages only when the product reporting policy permits reporting.
- Plugin messages must also pass their notification eligibility flag, the applied disabled state check, and the Ultimate restriction check.
- Descriptor failures and dependency cycles can supply global notification messages independently of plugin row problems.
- Repeated identical cycle messages appear once in the local calculation.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `global messages precede plugin problems and follow the reporting policy`
  )
- The startup reporter concatenates messages and action names from all registered notification handlers.
- With no messages, the reporter shows no notification.
- With messages, it shows one error notification with a separate paragraph for each message.
- The notification must offer navigation to the Plugins page with the Invalid filter.
- Startup reporting runs on application start outside the backend. It has no automatic trigger for later plugin set changes.

Untested: No focused reporter test covers global messages, handler aggregation, navigation, or notification delivery.

## Recovery Actions

- Notification recovery targets require problem notification eligibility and an affected plugin that initialization does not mark disabled or restrict through disabled Ultimate.
- An eligible problem registered during resolved-set analysis adds its affected plugin to the Disable targets.
- An eligible disabled dependency adds an Enable target when initialization marks it disabled and the candidate set contains its descriptor.
- Ultimate must not enter the generic Enable targets.
- Recovery maps keep one target per plugin ID. Candidate-selection problems do not add targets by themselves.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `loading error is cleared when disabled dependency and dependent are loaded`;
    `transitive disabled dependency reports the disabled root`;
    `missing dependency is exposed as a reason and disable suggestion`;
    `implementation detail errors are retained but not reported to the user`;
    `mixed disabled roots offer only ordinary recovery targets`;
    `a hidden problem does not add targets beside a visible problem with the same root`;
    `selection problems notify without adding recovery targets`;
    `optional module conflicts retain the last problem and a Disable target`
  )

- A nonempty Enable or Disable name collection adds its corresponding notification action.
- One name produces a named action. Several names produce the collective action.
- A notification action invokes the corresponding callback on every registered handler.
- The local handler resolves its recorded target IDs against the current plugin descriptors before it invokes the plugin enabler.

Untested: No focused test covers action labels, callback aggregation, or action invocation.

## Product Restrictions

- A plugin excluded through a required product mode module has kind `PRODUCT_MODE_MISMATCH`, severity `EXPECTED`, and notification eligibility false.
- This classification retains the originating mode and terminal module through intermediate plugins and required content modules.
- The explanation uses Light, frontend, or backend wording when applicable. Other modes use the generic product mode wording.
- These explanations have no trailing period.
- The Light wording takes priority for Light modes that also use the frontend.
- These problems contribute no notification recovery targets and offer no generic dependency Enable suggestion.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `a direct product mode exclusion has an expected explanation`;
    `a transitive product mode exclusion retains the expected root`;
    `a core product mode exclusion retains the expected root`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `product mode exclusions retain their page classification`;
    `disabling a product mode dependent suppresses its page problem`
  )

- Restrictions on custom plugins, the configured plugin subset, and all plugins except core produce expected explanations without notification eligibility.
- These problems retain kind `OTHER`. They contribute no notification recovery targets or generic dependency Enable suggestion.
- A recorded configuration restriction remains expected through intermediate plugins and required content modules.
- An optional content module alone must not exclude its owner. An unrelated failure retains its unexpected severity and notification data.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `configuration exclusions stay expected through required plugins and modules`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemUiTest.kt (
    `custom plugin restrictions use expected presentation until the restriction clears`;
    `subset restrictions use expected presentation until the restriction clears`;
    `core-only restrictions use expected presentation until the restriction clears`
  )

- An exclusion rooted in disabled Ultimate has kind `ULTIMATE_DISABLED`, severity `EXPECTED`, and notification eligibility false.
- This classification must survive intermediate plugins and required content modules.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `disabled Ultimate keeps the dependency problem but suppresses notification text`;
    `a transitive disabled Ultimate dependency suppresses notification text`;
    `a required content module preserves the disabled Ultimate root`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `disabled Ultimate has an expected non-notifying problem`
  )

- Other factories retain kind `OTHER` and severity `UNEXPECTED`.
- An unrelated failure retains its recorded cause, severity, and notification eligibility flag when the plugin also requires disabled Ultimate.
- This restriction suppresses that plugin's notification text and recovery targets, including for selection failures.
- The restriction follows required plugins, aliases, and content modules. Optional dependencies and optional content modules do not impose it.
- An unrestricted plugin retains its notification text and recovery targets, including when a restricted plugin shares its disabled root.
- Absent or failed Ultimate must not receive disabled Ultimate classification.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `disabled Ultimate suppresses notification of an unrelated missing dependency`;
    `disabled Ultimate suppresses notification but retains an ordinary disabled root`;
    `a transitive Ultimate requirement suppresses an unrelated notification`;
    `an Ultimate alias suppresses an unrelated notification`;
    `an Ultimate module requirement suppresses an unrelated notification`;
    `only required content modules impose the Ultimate notification restriction`;
    `an optional Ultimate dependency does not suppress an unrelated notification`;
    `disabled Ultimate suppresses a selection failure notification`;
    `an unrestricted plugin retains recovery beside a restricted plugin with the same root`;
    `absent Ultimate is reported as a missing dependency`;
    `failed Ultimate is not a disabled Ultimate problem`
  )

- Hidden Ultimate dependency problems must not contribute notification recovery targets.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `mixed notification data excludes hidden Ultimate recovery targets`;
    `mixed disabled roots offer only ordinary recovery targets`
  )

- The controller must retain disabled Ultimate classification without a generic Enable suggestion.
- The generic error area must exclude disabled Ultimate explanations and retain ordinary errors and Enable links.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `disabled Ultimate supplies its classification without an Enable suggestion`;
    `transitive disabled Ultimate supplies classification without an Enable suggestion`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemPresentationTest.kt (
    `a disabled Ultimate problem stays outside the generic error area`;
    `an ordinary loading problem retains its error text`;
    `an ordinary disabled dependency retains its Enable link`;
    `the page result retains Ultimate classification through serialization`;
    `a page result without Ultimate classification keeps its default`
  )

- An ordinary disabled dependency explanation must not offer Enable when its unresolved targets include Ultimate.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `an ordinary disabled root beside Ultimate does not offer to enable Ultimate`
  )

- Generic dependency recovery must exclude Ultimate even when a caller invokes it without a page suggestion.
- It must retain ordinary direct, transitive, and mixed recovery targets.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `generic recovery enables an ordinary direct dependency`;
    `generic recovery enables an ordinary transitive dependency`;
    `generic recovery does not enable direct Ultimate`;
    `generic recovery does not enable transitive Ultimate`;
    `generic recovery enables an ordinary root without enabling Ultimate`
  )

- The product availability check marks a plugin unavailable when it requires disabled Ultimate features.
- Product-restricted rows suppress ordinary loading errors. Their details view also skips ordinary loading error updates.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `product restricted rows suppress loading error text`
  )

Untested: No focused test covers the complete Ultimate restriction in details or the license recovery flow.

## State Changes

- A state request after plugin set replacement must use the replacement set.
- When a dependency and its affected plugin load, the new state must contain neither their obsolete problems nor their recovery targets.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingDependencyProblemTest.kt (
    `a missing module problem clears when its provider becomes available`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `loading error is cleared when disabled dependency and dependent are loaded`;
    `transitive disabled dependency reports the disabled root`;
    `making a product mode module available removes its dependent loading problem`
  )

- When Ultimate and its affected plugins load, their obsolete problems and recovery targets must disappear from the new state.
- Enabling Ultimate restores notification text and recovery targets for an unrelated failure that remains in the replacement set.
- The restriction check uses the supplied plugin set, not the global plugin set or disabled state.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `an Ultimate problem disappears when its dependency becomes available`;
    `enabling Ultimate restores notification of a remaining failure`;
    `Ultimate notification suppression uses the supplied plugin set`
  )

- The page must clear Ultimate classification when the session dependencies resolve or the user disables the affected plugin.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `disabling an Ultimate dependent suppresses its page problem`;
    `resolved Ultimate dependencies suppress the page problem`
  )

- Page dependency suggestions use the current plugin session, as specified under Plugins Page.
- Reused rows and selected details must update or remove obsolete expected explanations.
- Removing a product mode or configuration restriction must restore the normal row appearance and remove the details explanation.
- Clearing or changing the selection must remove the previous plugin's explanation from details.
- Expected explanations must retain both side prefixes and escape HTML characters as text.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `product mode exclusions gray the plugin and keep the toggle active on reused rows`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemUiTest.kt (
    `product mode exclusions gray rows and show details explanations until the mode changes`;
    `custom plugin restrictions use expected presentation until the restriction clears`;
    `subset restrictions use expected presentation until the restriction clears`;
    `core-only restrictions use expected presentation until the restriction clears`;
    `selected details refresh expected explanations without hiding errors`
  )

- The local notification handler retains the state captured on its first access for notification data and recovery targets.

Untested: No focused test verifies the notification handler's retained state after plugin set replacement.

## Verification

The linked tests verify the behavior beside each link. `Untested:` identifies implemented behavior without focused coverage.

The reference gate checks document structure, source targets, backlinks, and linked test names. It does not verify runtime behavior.

Run it from the repository root:

```shell
./community/tools/bt.cmd --module intellij.platform.ide.impl.tests --filter com.intellij.ide.plugins.PluginManagerSpecReferencesTest
```

## Open Questions

- Global messages, reporter actions, and product restrictions need focused coverage.
- Missing or incompatible dependency suggestions and optional module conflicts need focused coverage.
- Full row and details presentation needs coverage beyond controller results.
