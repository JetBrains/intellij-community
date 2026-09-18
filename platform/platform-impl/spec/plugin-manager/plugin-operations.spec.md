---
name: Plugin Operations
description: Stable behavior for plugin changes started or presented by the Plugins page.
targets:
  - ../../src/com/intellij/ide/plugins/UnifiedPluginsPageApplyState.kt
  - ../../src/com/intellij/ide/plugins/UnifiedPluginsPageSession.kt
  - ../../src/com/intellij/ide/plugins/newui/DefaultUiPluginManagerController.kt
  - ../../src/com/intellij/ide/plugins/newui/LegacyPluginUiHost.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginDetailsPageComponent.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginManagerCustomizer.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginModelAsyncOperationsExecutor.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginModelEvents.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginRowFactory.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginInstallingLedger.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllButton.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllController.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllExecutor.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageActions.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinator.kt
  - ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageApplyStateTest.kt
  - ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt
  - ../../testSrc/com/intellij/ide/plugins/newui/InstallPluginTerminalStateTest.kt
  - ../../testSrc/com/intellij/ide/plugins/newui/LegacyPluginUiHostTest.kt
  - ../../testSrc/com/intellij/ide/plugins/newui/PluginModelEventPublisherTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInstallingLedgerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllControllerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllExecutorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageStateTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt
---

# Plugin Operations

Status: Active
Date: 2026-09-18

## Purpose

The Plugins page starts and presents plugin installation, update, state, and removal operations.

This specification defines their stable lifecycle. The [UI contract](./unified-plugin-manager-ui.spec.md) defines their page presentation.

## Scope

This specification covers plugin operations that the Plugins page starts or presents.

### Goals

- Keep one operation state across all occurrences of a plugin.
- Preserve the plugin session while an apply or reset operation owns it.
- Make Update All progress and terminal states deterministic.

### Non-goals

- This specification does not define download or installation mechanics.
- This specification does not require one transaction across multiple plugins.
- This specification does not define split-mode routing.

## Operation Lifecycle

- The page must support install, update, enable, disable, and uninstall actions.

Untested: No focused page test covers the complete action set.

- Multiple physical targets must publish one logical operation lifecycle.
- A repeated physical start must not replace the logical presentation model.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginModelEventPublisherTest.kt (
    `two physical targets publish one logical lifecycle`;
    `repeated split target start keeps the logical presentation model`
  )

- A cancelled operation must not report success or require restart.
- A failed operation must report errors without requiring restart.
- A successful operation must preserve its restart result.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/InstallPluginTerminalStateTest.kt (
    `completed operation preserves its result`;
    `canceled operation cannot report success or restart`;
    `failed operation reports errors without restart`
  )

- A logical operation must require restart when any successful physical target requires restart.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginModelEventPublisherTest.kt (
    `logical operation publishes the combined restart result`
  )

## Installing Section

- An active install or update must appear in Installing before its destination inventory settles.
- An accepted dependency must appear in Installing while its operation is active.
- Each scheduled or installed dependency must appear once in its logical operation.
- Operation state must stay consistent across Installing, source rows, and plugin details.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInstallingLedgerTest.kt (
    `scheduled dependencies are active before operation completion`;
    `section projection combines operation model with current list facts`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginModelEventPublisherTest.kt (
    `scheduled dependencies are published before completion and deduplicated`;
    `installed dependencies are retained on the completed operation`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt

- Installing must retain each accepted attempt and its terminal result for the current page session.
- A retry must update the retained plugin occurrence without adding a duplicate.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInstallingLedgerTest.kt (
    `all terminal results are retained`;
    `retry updates the retained occurrence without duplicating it`
  )

- If an operation fails before a scheduled dependency installs, the dependency must stop and show the same failure result.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInstallingLedgerTest.kt (
    `operation failure terminates dependencies that were scheduled but not installed`
  )

## Settings Session

- Apply and OK must commit prepared changes through one plugin session.

Untested: No focused test verifies complete Apply and OK result handling.

- A successful Apply must clear prepared row and details state.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt (
    `apply clears prepared updates and keeps active downloads`
  )

- Disposal must close the plugin session exactly once when no operation retains it.
- An active apply must delay session close until the apply settles.
- A restart flow must retain the plugin session until restart.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageApplyStateTest.kt (
    `disposal without pending apply requests one session close`;
    `disposal waits until every apply operation settles`;
    `restart flow retains the session after disposal`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/LegacyPluginUiHostTest.kt (
    `session close waits for active logical operation`;
    `session close waits for submitted operation launcher`
  )

- Started downloads may continue in the application operation scope after the page closes.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/LegacyPluginUiHostTest.kt (
    `dispose hands operations to background before detaching UI and is idempotent`;
    `known background operation closes session after launcher completion`
  )

- The page must preserve the plugin session when it cannot determine whether a background operation remains active.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/LegacyPluginUiHostTest.kt (
    `unknown background operation conservatively preserves session`
  )

- A reopened page must start with a new Installing ledger and Update All request state.

Untested: No focused test verifies state isolation across two page sessions.

## Install and Update

- Before the trust check starts, an operation must load incomplete Marketplace details.
- An operation must stop before the trust check when complete Marketplace details are unavailable.
- The page must report the failure when complete Marketplace details are unavailable.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginActionDescriptorTest.kt (
    `install loads incomplete Marketplace details before the operation`;
    `install reports when Marketplace details cannot load`;
    `install reports when loaded Marketplace details remain incomplete`
  )

- A retained Update action must use the current descriptors after a source refresh.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `reused update button resolves the refreshed descriptor`
  )

- An install or update must show Downloading while its operation runs.
- A successful install or update must retain its Prepared state until Apply or Reset.
- A query change or source refresh must not clear the Prepared state.
- A failed or cancelled install or update must clear its operation state.
- Reset must clear prepared installs and updates.
- Apply must clear prepared installs and updates and keep active downloads.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt (
    `manual install state changes from downloading to prepared`;
    `manual update state changes from downloading to prepared`;
    `manual update ignores stale completion and clears on failure or reset`;
    `apply clears prepared updates and keeps active downloads`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `prepared manual update overrides a cached update in every occurrence`;
    `prepared Marketplace install stays installed after the query is repeated`
  )

- After progress ends, the row and details must replace their action controls with one prepared action.
- The details pane must not show an executable action while it changes to a prepared operation.
- A prepared operation without restart must show disabled Installed.
- A prepared operation that needs restart must show enabled Restart IDE.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `prepared dynamic update shows Installed and Reset restores Update`;
    `prepared update that needs restart shows Restart IDE in the row and details`;
    `prepared restart remains the only details action after progress ends`;
    `completed Marketplace install has one details action after progress ends`
  )

## Management Actions

- The Settings menu must offer repository, proxy, certificate, disk installation, Enable All Downloaded, and Disable All Downloaded actions.
- The menu must include product actions from the active customizer.
- It must offer automatic plugin updates only when the product policy permits them.
- Apply or OK must commit a changed automatic-update setting. Reset must restore the stored setting.

Untested: No focused test covers the complete Settings menu or the staged automatic-update setting.

- Repository, proxy, and certificate changes must refresh the page after a successful edit.
- A repository edit must give the active customizer the added and removed repository URLs.

Untested: No focused test covers every management edit and refresh path.

- Enable All and Disable All must use the complete query-independent Installed section.
- These actions must not include Bundled or remote-source sections.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageStateTest.kt (
    `bulk state actions use only installed section models`
  )

## Update All

- Update All must remain unavailable until the local inventory resolves each update to an installed plugin.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `Update All waits for the local plugin inventory`
  )

- Update All must capture the current enabled update set when the user starts it.
- Disabled updates must not enter an Update All request.
- The captured total must stay fixed while that request runs.
- A successful prepared update must advance the prepared count.
- A failed or cancelled update must not advance the prepared count.
- Any failed target must make the completed request retryable.
- Retry must include only failed targets that remain eligible.
- A restart requirement must produce the Restart IDE terminal state.
- Completion without restart must produce the Updated terminal state.
- A later update snapshot must replace an obsolete terminal state.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllControllerTest.kt (
    `request captures one snapshot before executor starts`;
    `failed operation is retained after the other updates prepare`;
    `restart requirement survives a retry`;
    `completion without restart shows updated state`
  )

## Operation Presentation

- Installing must appear before all source sections when it has visible occurrences.
- Installing must not change in response to the search query.
- Update All must stay visible and disabled while its request runs.
- Update All must show prepared and total counts while its request runs.
- Active operation progress must appear in each matching row and in the selected plugin details.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllControllerTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `Update All button counts prepared executor targets`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `active update renders row and details progress without action overlap`
  )

## Operation Integration

- Plugin identity must join operation events with all matching page occurrences.
- Inventory refreshes must remain authoritative for the final installed state.
- Update All must use the same per-plugin update path as a manual update.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllExecutorTest.kt (
    `executor starts each update with its installed plugin`
  )

- The page must use the active product customizer for operation actions and post-change refreshes.

Untested: No focused test verifies all product operation hooks on the unified page.

## Failure and Recovery

- One failed Update All target must not hide successful target results.
- A failed physical target must take precedence over a missing target.
- A missing physical target must complete as cancellation.
- A failed logical operation must not publish a restart result.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginModelEventPublisherTest.kt (
    `physical failure takes precedence over a missing target`;
    `missing physical target completes as cancellation`;
    `failed operation does not publish a restart result`
  )

- An event from an obsolete operation must not change a newer request.
- A closed page controller must ignore later operation callbacks.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllControllerTest.kt (
    `stale callback cannot change a newer request`;
    `closed controller ignores executor callbacks`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginUpdateAllExecutorTest.kt (
    `executor reports target start failures and continues`
  )

## Verification

The linked tests need no setup beyond the standard module test command.

## Open Questions

- End-to-end Cancel and Reset behavior needs a stable contract and focused coverage.
- Displayed retry behavior needs broader end-to-end coverage.
