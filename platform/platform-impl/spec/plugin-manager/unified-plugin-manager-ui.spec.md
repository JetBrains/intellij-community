---
name: Unified Plugin Manager UI
description: Stable behavior for the unified Plugins page, its sources, and its navigation.
targets:
  - ../../src/com/intellij/ide/plugins/PluginCategoryPromotionProvider.kt
  - ../../src/com/intellij/ide/plugins/PluginManagerConfigurable.kt
  - ../../src/com/intellij/ide/plugins/PluginManagerConfigurableTreeRenderer.java
  - ../../src/com/intellij/ide/plugins/PluginsPageSession.kt
  - ../../src/com/intellij/ide/plugins/UnifiedPluginsPageFeature.kt
  - ../../src/com/intellij/ide/plugins/UnifiedPluginsPageSession.kt
  - ../../src/com/intellij/ide/plugins/marketplace/statistics/UnifiedPluginSearchStatistics.kt
  - ../../src/com/intellij/ide/plugins/marketplace/statistics/collectors/PluginManagerFUSCollector.kt
  - ../../src/com/intellij/ide/plugins/marketplace/statistics/collectors/PluginManagerMPCollector.kt
  - ../../src/com/intellij/ide/plugins/newui/ListPluginComponent.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginDetailsPageComponent.kt
  - ../../src/com/intellij/ide/plugins/newui/PluginImagesComponent.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginDetailsPresenter.kt
  - ../../src/com/intellij/ide/plugins/unified/PluginRowReconciler.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginInternalSourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginInventory.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProvider.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProvider.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginRepositoryCache.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/LegacyPluginRowFactory.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginFocusBorder.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginRowEventHandler.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageController.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageActions.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinator.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageState.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageStatistics.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsPageView.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsQuery.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsSearchActions.kt
  - ../../src/com/intellij/ide/plugins/unified/UnifiedPluginsSearchToolbar.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginManagerConfigurableRoutingTest.kt
  - ../../testSrc/com/intellij/ide/plugins/PluginManagerConfigurableTreeRendererTest.kt
  - ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageFeatureTest.kt
  - ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt
  - ../../testSrc/com/intellij/ide/plugins/marketplace/statistics/UnifiedPluginSearchStatisticsTest.kt
  - ../../testSrc/com/intellij/ide/plugins/newui/PluginImagesComponentTest.kt
  - ../../testSrc/com/intellij/ide/plugins/newui/PluginRowRenderKeyTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInternalSourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInventoryTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProviderTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositoryCacheTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRowEventHandlerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageStateTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsQueryTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsSearchActionsTest.kt
  - ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsSearchToolbarTest.kt
---

# Unified Plugin Manager UI

Status: Active
Date: 2026-09-22

## Purpose

The unified Plugins page presents installed and available plugins from several sources in one searchable page.

This specification defines stable page behavior. [Plugin Operations](./plugin-operations.spec.md) defines changes to installed plugins.

## Scope

This specification covers the page structure, section model, search, source loading, and product extensions.

### Goals

- Give each plugin source an independent section and loading state.
- Keep search results consistent with the latest query.
- Define stable section membership and product extension behavior.

### Non-goals

- This specification does not define incidental insets, exact spacing, or Swing implementation classes.
- This specification does not require the legacy search completion popup or its shortcut.
- This specification does not define the rollout mechanism.
- This specification does not assign classes to architecture layers.

## Page Structure

- The Plugins page must use one search field for all sources.
- The page must show source sections on the left and details for an explicit selection on the right.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `session renders the unified shell and supports compatibility search`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `compatible multi selection renders every row and details occurrence`
  )

- Visible sections must use this order: Installing, Installed, Bundled, Suggested or Marketplace, Internal, then custom repositories.
- A ready section with no items must not leave a placeholder.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `sections use semantic order and preserve custom repository order`;
    `default ready sections are hidden`
  )

- Custom repository content must stay hidden for an empty query.
- A custom repository or repository catalog failure must remain visible without a query.
- An active Repository filter must keep each selected repository visible after a successful empty result.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `failed custom repository remains visible without a query until retry`;
    `repository filter keeps its empty selected repository visible`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `repository catalog failure remains visible without a query`
  )

## Section Membership

- Installed must contain plugins that have no bundled origin on any available side.
- Bundled must contain plugins that have a bundled origin on at least one available side.
- A staged descriptor must replace runtime presentation without losing the bundled origin.
- The inventory must hide a plugin when any side marks it as an implementation detail.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInventoryTest.kt (
    `custom plugin stays in installed section with its side facts`;
    `staged descriptor supersedes runtime presentation and keeps bundled origin`;
    `implementation detail plugin is excluded when either side marks it hidden`
  )

- Several physical entries with one plugin ID must produce one inventory item.
- That item must use local presentation and retain the bundled origin from every available side.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInventoryTest.kt (
    `same id on both sides prefers local presentation and retains remote bundled origin`
  )

- When the page shows local sections, a plugin ID in Installed or Bundled must not appear in a remote section.
- This exclusion must use the complete local inventory before the page applies query filters.
- When a source filter hides local sections, the active remote sections can show these plugin IDs.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `local plugins are excluded from Marketplace and repository sections`;
    `local plugins are excluded from Suggested`;
    `repository filter shows local plugins when local sections are hidden`
  )

- Suggested must combine project suggestions before Staff Picks.
- Suggested must keep one result per plugin ID and prefer the project suggestion.
- Suggested must publish a completed source before the other source completes.
- Suggested must keep successful results when one suggestion source fails.
- Suggested must not report custom repository errors as Suggested errors.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt (
    `merged suggestions keep project priority and partial errors`;
    `Staff Picks publish while project suggestions are still loading`;
    `merged suggestions isolate a failed source`;
    `merged suggestions omit only custom repository errors`
  )

- Marketplace must keep one stable occurrence for each plugin ID.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt (
    `normalization keeps one stable occurrence per plugin id`
  )

- An internal group must use the title and plugins that the product provides.
- The page must omit Internal when the product provides no internal group.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInternalSourceCoordinatorTest.kt (
    `internal group is enriched and published with its custom title`;
    `missing internal group does not publish a section or enrich data`
  )

## Search and Navigation

- An empty query must make local, Suggested, Internal, and custom repository sources eligible.
- A general nonempty query must replace Suggested with Marketplace.
- An Installed navigation request must search local plugins without starting other source searches.
- A Marketplace navigation request must keep local sections unfiltered and search Marketplace.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `query scope changes source intent without changing visible query`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerConfigurableRoutingTest.kt (
    `unified Installed navigation applies immediately`
  )

- An initial Marketplace navigation must start its search without loading Suggested.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `initial Marketplace navigation skips Suggested loading`
  )

- An installed filter or update-source constraint must exclude Marketplace, Internal, and custom repository results.
- A repository filter must show only the selected custom repositories.
- Text, Vendor, Category, and Tag constraints must apply to each eligible source under that source's rules.
- A query with installed-status and Repository constraints must leave every filtered source ready and empty.
- Suggested, Staff Picks, and Internal commands must route only to their applicable sources.
- The last installed filter in a query must take effect.
- Search controls must preserve unrelated manual query terms.
- Relevance must be the default sort when the query has no supported sort command.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsQueryTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `category filter applies to each locally projected source`;
    `repository filter targets one cached section and keeps local sections unfiltered`;
    `installed and repository constraints leave every filtered source ready and empty`
  )

- The filter control must offer Tag, Vendor, Category, Repository, and installed-status filters.
- Repository choices must appear only when repository values exist.
- Tag, Vendor, Category, and Repository filters must allow multiple selections.
- Installed-status filters must allow one optional selection.
- Installed-status choices must include Update available, Enabled, Disabled, Invalid, and Updated bundled.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsSearchActionsTest.kt (
    `filter actions reflect snapshot and emit semantic intents`;
    `repository filter is hidden without repository values`;
    `installed actions share an optional radio selection`
  )

- An empty search field must show `Search all plugins` and the Filter control.
- The Filter control must show a selected state while a represented filter is active.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `search renders placeholder and ordered focusable controls`
  )

- An active filter value must remain available when the current source facts do not contain it.
- Tag choices must put all Marketplace tags first and retain their Marketplace order.
- Marketplace tag choices must contain all Marketplace tags.
- The highest plugin count must come first, with tag name as the tie-breaker.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `filter options combine unfiltered source metadata and selected absent values`;
    `filter options put all Marketplace tags before other tags`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt (
    `Marketplace tags keep all values in count order`;
    `Marketplace tags use name order for equal counts`
  )

- A long Repository choice must expose its full value in its accessible description and popup tooltip.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsSearchActionsTest.kt (
    `long repository action exposes full value as description and popup tooltip`
  )

- Marketplace must translate Category filters to tag constraints without changing the text query.
- Equal Category and Tag filters must produce one Marketplace tag constraint.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceDataProviderTest.kt (
    `Marketplace maps category filters to tags without changing text search`;
    `Marketplace does not duplicate equal category and tag filters`
  )

- The sort control must offer Relevance, Downloads, Rating, Name, and Updated as one required selection.
- The sort control must appear only when the current route contains a sortable source.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsSearchActionsTest.kt (
    `sort actions use radio group order and emit one selection`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `search renders placeholder and ordered focusable controls`
  )

- The first result for a query revision must set the plugin order in each section.
- Relevance must initially place local plugin errors before healthy plugins, then use the existing match scores.
- Later results in the same query revision must retain the relative order of plugins that remain in a section.
- A new plugin must append after existing plugins. A plugin that leaves and returns must resume its retained position.
- In expanded Bundled, a new plugin must append in its category. A new category must append after existing categories.
- The first collapsed and expanded Bundled results may set separate orders to keep categories together.
- An explicit sort must override local error priority and control Marketplace ordering. Custom repositories must retain their source order.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `local relevance prioritizes errors while explicit sorts override that priority`;
    `installed relevance uses legacy name and description match scores`;
    `sort controls primary Marketplace without suppressing cached filtering`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `query revision keeps local row order across repeated refreshes`;
    `new plugins append, returning plugins resume, and a new query resets order`;
    `expanded Bundled appends new plugins inside their category`
  )

- Search history must persist across page views.
- The standard history actions must update the query through the normal search path.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `search history popup is enabled and history persists across views`;
    `search history shortcuts navigate queries through the normal callback`
  )

- The Plugins search field must be the preferred focus component.
- A Spotlight search must update the query without moving Settings focus.
- A direct Settings search request must move focus to the Plugins search field.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `session renders the unified shell and supports compatibility search`;
    `Spotlight search applies its query without requesting focus`
  )

- The latest page input, Settings search action, or navigation request must win over an older deferred request.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `stale Settings clear does not replace Marketplace navigation`;
    `explicit empty Settings request clears Marketplace navigation`;
    `page input invalidates a pending Settings action`;
    `only the latest pending Settings action applies`
  )

- Enter in the Plugins search field must consume the event and keep Settings open.

Untested: Community tests verify the Enter handler, but they do not verify the Settings dialog result.

## Search Reporting

- Each nonempty query must emit one unified search event after all eligible sources settle.
- The event must report the query shape, filter kinds, source kinds, effective sort, and each section result count.
- The event must not report filter values or repository identities.
- A session start event must identify the unified page.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `session reports each non-empty search after all sources settle`;
    `session started identifies the unified page`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/marketplace/statistics/UnifiedPluginSearchStatisticsTest.kt (
    `MP collector inherits the unified schema`;
    `unified search reports bounded controls sources and section counts`
  )

- Clearing a nonempty query must emit one search-reset event.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `clearing a non-empty query records one search reset`
  )

- A plugin card event must report its position in the complete section result, not only the rendered rows.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `row group keeps the full section order`
  )

## Source Loading

- Each source must load independently from other sources.
- A completed source must update its section without waiting for other sources.
- A result from an obsolete query or source revision must not replace a current result.
- Local filtering must inspect all source items, including items beyond the display limit.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinatorTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt (
    `catalog refresh removes a repository and rejects its late completion`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `search includes plugins beyond the section display limit`
  )

- A hidden page must defer source rendering.
- When the page becomes visible, it must render the latest source state.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `page replays source state when it first becomes visible`
  )

## Page Interaction

- A loading section must show a loading state instead of a result count.
- A collapsed section must limit visible items and offer an expansion action when more items exist.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt

- Expansion and collapse must clear row selection and plugin details.
- A section must keep its expansion state if it temporarily leaves the page during the same session.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `section expansion changes clear selection`;
    `expansion persists while a section is absent`
  )

- With the default density, a collapsed section must show at most three plugins.
- An expanded section must show at most 1,000 plugins without truncating its source inventory.
- A larger result count must appear as `1000+`.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `collapsed section projects three items and loading replaces count`;
    `large sections cap display without truncating inventory`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `large section count shows the display limit`
  )

- Installing must expand once when accepted operations first exceed the collapsed limit.
- A later manual Installing collapse must remain effective during source updates.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `Installing auto-expands when it first becomes expandable`;
    `manual Installing collapse survives later source replacements`
  )

- Suggested and Marketplace must start expanded.
- A manual collapse of either section must remain effective during later source updates.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `Suggested and Marketplace start expanded and preserve manual collapses`
  )

- A query that targets only local sections must expand Installed and Bundled.
- A Repository filter must expand each selected custom repository section.
- Automatic expansion must not expand Installing or change the selection.
- A manual collapse must remain effective while text, facets, or sorting change in the same automatic expansion context.
- A navigation scope change must preserve that collapse while an explicit local filter stays active.
- A changed local filter or update source must start a new automatic expansion context.
- A newly selected repository must expand. An unchanged selected repository must retain its manual collapse.
- When an automatic expansion context ends, each section must restore its session expansion state.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `installed filter expands local sections without changing the Installing section`;
    `local automatic collapse follows its semantic filter context`;
    `Installed navigation scope does not restart an explicit local filter context`;
    `update source value starts a new local automatic expansion context`;
    `repository filters expand new sections and retain collapses for selected repositories`
  )

- Search controls and section expansion actions must be keyboard reachable and have accessible names.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `search renders placeholder and ordered focusable controls`;
    `expandable section header supports mouse keyboard hover and focus`
  )

- Plugin rows and section headers must show an outline for keyboard focus.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified rows use stable island selection geometry`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `expandable section header supports mouse keyboard hover and focus`
  )

- A plugin enablement toggle must retain its component and focus during a compatible row refresh.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified rows use toggle for plugin enablement`
  )

- Activating row content must select the plugin without invoking its primary action.
- Enter and Space on a focused row must select the plugin without invoking its primary action.
- Row action controls must run their action without activating the row.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `card activation selects plugin without invoking its primary action`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRowEventHandlerTest.kt (
    `buttons and their children are action controls`;
    `ordinary row content remains row activation content`
  )

- Right-click must request a row context menu on all platforms.
- Control-click must request a context menu on macOS and toggle selection on other platforms.
- Command-click must toggle selection on macOS.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRowEventHandlerTest.kt (
    `control click requests a context menu only on macOS`;
    `toggle selection modifier follows platform conventions`
  )

- Multi-selection may span Installing, Installed, and Bundled.
- It may separately span Internal, Suggested, Marketplace, and custom repositories.
- Selecting a plugin from the other group must replace the incompatible selection.
- The details panel must receive all compatible selected plugins.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `selection spans compatible local sections but not operation modes`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `compatible multi selection renders every row and details occurrence`
  )

- An independent source update must preserve the current explicit selection or explicitly cleared selection.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `remote completion does not steal local selection`;
    `remote completion does not replace explicitly cleared selection`;
    `installing publication preserves empty selection across later source updates`
  )

- When a new search query sets a default selection, the selected row must be visible below the sticky header.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `new query reveals its default selection after the previous anchor disappears`;
    `new query moves its selected anchor below the sticky header`
  )

- Shift-selection may cross section headers but must skip plugins from the other selection group.
- Select All must select all compatible rendered rows.

Untested: No focused test verifies Shift-selection or Select All across sections.

- The page must show the standard empty status when no section is visible.
- Accessible result announcements must distinguish loading, completion, and failure.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `no visible sections show the standard empty status`;
    `result announcements describe loading completion and errors`
  )

- The current section header must remain visible while its rows scroll.
- The next section header must replace it without duplicating its controls.
- A sticky expansion control must remain functional.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `sticky header is pushed off by the next section without duplicating controls`;
    `sticky expansion control collapses its section`
  )

- A long section title must yield space to its count and actions.
- A shortened title must expose its full value as its accessible name and tooltip.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `long repository title truncates without hiding controls`;
    `long non-repository section title uses trailing ellipsis`
  )

- A section insertion, removal, expansion, or row height change must preserve the visible plugin offset.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `inserting and removing a section preserves visible occurrence offset`;
    `expanding a preceding section preserves visible occurrence offset`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `installing row height change preserves a later visible occurrence`;
    `reordered real rows preserve the top visible occurrence after a height change`;
    `same query refreshes preserve real-row order and visible offset`
  )

## Presentation

- The header must show Search, Update All, and Settings controls in that order.
- Search must shrink before a header action is clipped.
- The unified page must hide the shared Settings progress and Reset controls.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageSessionTest.kt (
    `search keeps its maximum with internal controls and shrinks for header actions`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerConfigurableRoutingTest.kt (
    `unified page hides shared header status controls`;
    `unified header keeps controls after the title`
  )

- Install, Update, and Restart actions must use the secondary button style in unified rows and details.
- The legacy Plugins page must keep its existing action button style.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified host uses secondary plugin action buttons`;
    `legacy plugin action buttons keep custom colors`
  )

- Unified rows and details must show visible plugin tags as accessible badges.
- A plugin that requires an unavailable product edition must add the applicable Ultimate or Pro tag.
- Paid, Ultimate, and Pro tags must use the blue secondary type.
- Freemium and Purchased tags must use the green secondary type. Other tags must use the neutral secondary type.
- A compact row must hide neutral tags and show at most the first colored tag.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified tag panels use badge colors and legacy panels keep tag components`;
    `unified host uses marketplace badge tags in rows and details`;
    `unified rows hide gray tags`;
    `unified installed rows keep the first colored tag`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProviderTest.kt (
    `bulk product restriction adds a product tag`
  )

- The page must use the 32-pixel icon density and compact rows by default.
  [@test] ../../testSrc/com/intellij/ide/plugins/UnifiedPluginsPageFeatureTest.kt (
    `density variant uses 32 pixel icons by default`;
    `density variant selects 32 pixel icons`
  )

- A row refresh must preserve all customized tags.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginRowRenderKeyTest.kt (
    `all tags are constructor presentation`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified row uses tags captured by its render key`
  )

- A plugin error must extend below the action column and use the available row width.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `unified error spans the action column`
  )

- Plugin details must use single-row tabs and offer an overflow menu when the tabs do not fit.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/LegacyPluginRowFactoryTest.kt (
    `details page spacing is enabled only for the unified page`
  )

- The screenshot carousel must fit inside both details insets so that its controls remain available.
  [@test] ../../testSrc/com/intellij/ide/plugins/newui/PluginImagesComponentTest.kt (
    `image width accounts for both parent insets`;
    `image width does not become negative`
  )

## Product Integration

- Internal must use the standard section expansion actions instead of the legacy custom Show All query.

Untested: No focused test verifies that Internal ignores the legacy Show All query.

- Bundled must use Other for a missing category.
- Default Relevance must sort category names with case sensitivity and put Other last.
- Default Relevance must sort plugin names within each category.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalDataProviderTest.kt (
    `local snapshot assigns bundled categories and normalizes a missing category`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `bundled relevance sorts exact categories and names with Other last`
  )

- An expanded Bundled section must group plugins by category when the query is empty.
- Each category must offer Enable All or Disable All for the complete category.
- Category actions must include plugins beyond the display limit.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `expanded Bundled section publishes category actions only without a query`;
    `Bundled category action includes plugins beyond the display limit`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `Bundled category headers follow expansion and query state`;
    `Bundled category action includes unrealized rows and later categories appear while scrolling`
  )

- With an empty query, a priority provider must move its exact Bundled category before healthy nonpriority categories.
- The initial Bundled order must keep error items before healthy priority items.
- The initial expanded Bundled order must keep each category together and place error categories before healthy categories.
- A promotion panel must appear after its category header only while Bundled is expanded.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageControllerTest.kt (
    `Bundled category priority applies without a query and matches exact case`;
    `collapsed Bundled section keeps errors ahead of provider priority`;
    `expanded Bundled section keeps error categories contiguous and ahead of provider priority`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageRealRowsTest.kt (
    `Bundled promotion appears after its category header only while expanded`
  )

- The Settings tree must show a nonzero plugin update count as an accessible neutral badge.
- The Settings tree must hide the badge when the update count becomes zero.
  [@test] ../../testSrc/com/intellij/ide/plugins/PluginManagerConfigurableTreeRendererTest.kt (
    `update count uses a gray secondary badge and hides zero`
  )

## Source Model

- Local inventory must become usable without waiting for Marketplace or custom repositories.
- Each custom repository must keep its own identity, order, and section state.
- Filter options must use unfiltered source facts when those facts are available.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt

- Local installed models, errors, and installation states must override conflicting remote facts.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `local list model facts override remote facts`
  )

- Consumers in one page session must share custom repository requests.
- Query changes must filter cached repository content without another request. A new page session must request fresh content.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositoryCacheTest.kt (
    `concurrent consumers share catalog and repository requests for one page`;
    `a new page cache obtains a fresh response`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageSourceCoordinatorTest.kt (
    `query filters repository sections without refetching and preserves source order`
  )

- Visible custom repository sections may repeat a plugin ID.
- Their combined operation inventory must keep the newest version for each plugin ID.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt (
    `repository projection keeps the newest duplicate version`
  )

- Plugin state changes must update cached Internal, Marketplace, and repository rows without another source request.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInternalSourceCoordinatorTest.kt (
    `plugin updates refresh internal row data`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinatorTest.kt (
    `updates re-enrich fetched models without another request`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt (
    `plugin updates re-enrich cached models without repository refetch`
  )

## Failure and Recovery

- A refresh must retain usable source content while the new request loads.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginsPageViewTest.kt (
    `loading icon replaces count while retaining items`
  )

- A source failure must not remove successful sections from other sources.
- A failed source must differ from a successful empty result.
- A failed section must offer retry when the source supports retry.
- A failed refresh must keep usable stale content and mark that content as degraded.
- A successful retry must replace the failed or degraded state.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginInternalSourceCoordinatorTest.kt (
    `initial descriptor failure publishes a retryable section`
  )
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginLocalSourceCoordinatorTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinatorTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositoryCacheTest.kt (
    `failed refresh does not evict the last page-cached success`
  )

- Popular tag loading and failure must not delay or fail Marketplace content.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginMarketplaceSourceCoordinatorTest.kt (
    `popular tags load independently from Marketplace content`;
    `popular tag failure keeps Marketplace content ready`
  )

- A custom repository retry must reload only that repository.
- Suggested must refresh after the repository settles.
  [@test] ../../testSrc/com/intellij/ide/plugins/unified/UnifiedPluginRepositorySourceCoordinatorTest.kt (
    `retry reloads only one repository and refreshes suggestions after it settles`
  )

## Verification

The linked tests need no setup beyond the standard module test command.

## Open Questions

- Initial selection outside search is not a stable contract.
- Duplicate handling between Marketplace and custom repository sections needs a stable contract and end-to-end coverage.
