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
  - ../../src/com/intellij/ide/plugins/newui/DefaultUiPluginManagerController.kt
  - ../../src/com/intellij/ide/plugins/newui/MyPluginModel.kt
  - ../../src/com/intellij/ide/plugins/newui/ErrorComponent.java
  - ../../src/com/intellij/ide/plugins/newui/ListPluginComponent.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginDetailsPageComponent.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginRowFactory.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginDetailsPresenter.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemPresentationTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt
---

# Plugin Loading Problems

Status: Active
Date: 2026-10-13

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

- Each plugin ID exposes at most one loading problem.
- A plugin problem supplies a short page explanation, a detailed notification explanation, and notification eligibility.
- Messages use localized templates and the descriptor data available when the consumer reads the message.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`;
    `messages read the current descriptor data`
  )

- A disabled root of a required dependency chain must remain identifiable through intermediate plugins and content modules.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `transitive disabled dependency reports the disabled root`;
    `several intermediate plugins report one disabled root`;
    `a required content module reports the transitive disabled root`;
    `a core content module reports the disabled root instead of IDEA CORE`
  )

- For other chains that cross a plugin boundary, the explanation refers to the first required plugin outside the affected plugin.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `a transitive product mode exclusion reports failed intermediate plugins`;
    `a core product mode exclusion reports IDEA CORE`
  )

- For a chain within one plugin, analysis uses the terminal exclusion when it has a supported explanation.
- For an unresolved dependency without a cross-plugin boundary, the installation requirement message uses the plugin ID or content module name.
- For an invisible dependency without a cross-plugin boundary, that message uses the dependency module's plugin ID.
- Supported compatibility and selection exclusions can also produce plugin explanations.
- A package-prefix conflict in an optional content module can produce an explanation while the main plugin remains loaded.

Untested: Focused analysis tests do not cover all terminal mappings or optional module conflicts.

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

- Direct environment exclusions and unused on-demand modules produce no loading explanation.
- Direct AI disablement, expired license, and unaccepted privacy exclusions produce no loading explanation from this analysis.
- Direct superseded versions produce no loading explanation.
- A chain across plugin boundaries follows the dependency selection rule even when its terminal exclusion has no direct explanation.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `a direct product mode exclusion has no loading explanation`;
    `a product mode exclusion does not suppress an unrelated missing dependency`
  )

- An unavailable optional module alone must not produce a loading problem for its loaded plugin or dependents.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `an optional product mode module does not produce a loading problem`
  )

Untested: Focused tests do not cover every direct exclusion and its transitive dependents.

## Plugins Page

- An ordinary loading problem without a disabled dependency supplies its short explanation to the page.
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
- Plugin messages must also pass their notification eligibility flag and the applied disabled state check.
- Descriptor failures and dependency cycles can supply global notification messages independently of plugin row problems.
- Repeated identical cycle messages appear once in the local calculation.
- The startup reporter concatenates messages and action names from all registered notification handlers.
- With no messages, the reporter shows no notification.
- With messages, it shows one error notification with a separate paragraph for each message.
- The notification must offer navigation to the Plugins page with the Invalid filter.
- Startup reporting runs on application start outside the backend. It has no automatic trigger for later plugin set changes.

Untested: No focused reporter test covers global messages, handler aggregation, navigation, or notification delivery.

## Recovery Actions

- Notification recovery targets require problem notification eligibility and an affected plugin that initialization does not mark disabled.
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
    `a hidden problem does not add targets beside a visible problem with the same root`
  )

- A nonempty Enable or Disable name collection adds its corresponding notification action.
- One name produces a named action. Several names produce the collective action.
- A notification action invokes the corresponding callback on every registered handler.
- The local handler resolves its recorded target IDs against the current plugin descriptors before it invokes the plugin enabler.

Untested: No focused test covers action labels, callback aggregation, or action invocation.

## Product Restrictions

- Explicit restrictions on non-bundled plugins and the configured plugin subset produce explanations without notification eligibility.
- Complete loading disablement produces an explanation with notification eligibility.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemTest.kt (
    `loading problem factories preserve their presentation`
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

- Other factories retain `OTHER` kind and `UNEXPECTED` severity independently of their notification eligibility.
- Requiring disabled Ultimate must not suppress an unrelated recorded failure.
- Absent or failed Ultimate must not receive disabled Ultimate classification.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `disabled Ultimate does not suppress an unrelated missing dependency`;
    `an ordinary disabled root stays unexpected when Ultimate is also disabled`;
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
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `loading error is cleared when disabled dependency and dependent are loaded`;
    `transitive disabled dependency reports the disabled root`;
    `making a product mode module available removes its dependent loading problem`
  )

- When Ultimate and its affected plugins load, their obsolete problems and recovery targets must disappear from the new state.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerStateServiceTest.kt (
    `an Ultimate problem disappears when its dependency becomes available`
  )

- The page must clear Ultimate classification when the session dependencies resolve or the user disables the affected plugin.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginLoadingProblemControllerTest.kt (
    `disabling an Ultimate dependent suppresses its page problem`;
    `resolved Ultimate dependencies suppress the page problem`
  )

- Page dependency suggestions use the current plugin session, as specified under Plugins Page.
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
