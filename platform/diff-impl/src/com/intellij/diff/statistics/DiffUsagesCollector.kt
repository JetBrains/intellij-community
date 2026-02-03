// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.statistics

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.base.HighlightPolicy
import com.intellij.diff.tools.util.base.HighlightingLevel
import com.intellij.diff.tools.util.base.IgnorePolicy
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.tools.util.breadcrumbs.BreadcrumbsPlacement
import com.intellij.diff.util.DiffPlaces
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class DiffUsagesCollector : ApplicationUsagesCollector() {

  private val GROUP = EventLogGroup("vcs.diff", 9)
  private val places = listOf(DiffPlaces.DEFAULT,
                              DiffPlaces.CHANGES_VIEW,
                              DiffPlaces.VCS_LOG_VIEW,
                              DiffPlaces.VCS_FILE_HISTORY_VIEW,
                              DiffPlaces.COMMIT_DIALOG,
                              DiffPlaces.MERGE,
                              DiffPlaces.TESTS_FAILED_ASSERTIONS)

  private val DIFF_PLACE = EventFields.String("diff_place", places)
  private val SYNC_SCROLL = GROUP.registerVarargEvent("sync.scroll", DIFF_PLACE, EventFields.Enabled)
  private val ALIGNED_CHANGES = GROUP.registerVarargEvent("aligned.changes", DIFF_PLACE, EventFields.Enabled)
  private val IGNORE_POLICY_VALUE = EventFields.Enum("value", IgnorePolicy::class.java)
  private val IGNORE_POLICY = GROUP.registerVarargEvent("ignore.policy", DIFF_PLACE, IGNORE_POLICY_VALUE)
  private val HIGHLIGHT_POLICY_VALUE = EventFields.Enum("value", HighlightPolicy::class.java)
  private val HIGHLIGHT_POLICY = GROUP.registerVarargEvent("highlight.policy", DIFF_PLACE, HIGHLIGHT_POLICY_VALUE)
  private val CONTEXT_RANGE_VALUE = EventFields.Int("value")
  private val CONTEXT_RANGE = GROUP.registerVarargEvent("context.range", DIFF_PLACE, CONTEXT_RANGE_VALUE)
  private val HIGHLIGHTING_LEVEL_VALUE = EventFields.Enum("value", HighlightingLevel::class.java)
  private val SHOW_WARNINGS_POLICY = GROUP.registerVarargEvent("show.warnings.policy", DIFF_PLACE, HIGHLIGHTING_LEVEL_VALUE)
  private val COLLAPSE_UNCHANGED = GROUP.registerVarargEvent("collapse.unchanged", DIFF_PLACE, EventFields.Enabled)
  private val SHOW_LINE_NUMBERS = GROUP.registerVarargEvent("show.line.numbers", DIFF_PLACE, EventFields.Enabled)
  private val SHOW_WHITE_SPACES = GROUP.registerVarargEvent("show.white.spaces", DIFF_PLACE, EventFields.Enabled)
  private val SHOW_INDENT_LINES = GROUP.registerVarargEvent("show.indent.lines", DIFF_PLACE, EventFields.Enabled)
  private val USE_SOFT_WRAPS = GROUP.registerVarargEvent("use.soft.wraps", DIFF_PLACE, EventFields.Enabled)
  private val ENABLE_READ_LOCK = GROUP.registerVarargEvent("enable.read.lock", DIFF_PLACE, EventFields.Enabled)
  private val BREADCRUMBS_PLACEMENT_VALUE = EventFields.Enum("value", BreadcrumbsPlacement::class.java)
  private val SHOW_BREADCRUMBS = GROUP.registerVarargEvent("show.breadcrumbs", DIFF_PLACE, BREADCRUMBS_PLACEMENT_VALUE)
  private val MERGE_APPLY_NOT_CONFLICTED = GROUP.registerVarargEvent("merge.apply.non.conflicted", DIFF_PLACE, EventFields.Enabled)
  private val MERGE_AUTO_RESOLVE_IMPORT_CONFLICTS = GROUP.registerVarargEvent("merge.resolve.import.conflicts", DIFF_PLACE, EventFields.Enabled)
  private val MERGE_ENABLE_LST_MARKERS = GROUP.registerVarargEvent("merge.enable.lst.markers", DIFF_PLACE, EventFields.Enabled)
  private val USE_UNIFIED_DIFF = GROUP.registerVarargEvent("use.unified.diff", DIFF_PLACE, EventFields.Enabled)
  private val ITERATE_NEXT_FILE = GROUP.registerVarargEvent("iterate.next.file", DIFF_PLACE, EventFields.Enabled)
  private val ENABLE_EXTERNAL_TOOLS = GROUP.registerVarargEvent("enable.external.diff.tools", DIFF_PLACE, EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val set = HashSet<MetricEvent>()

    for (place in places) {
      val data: List<EventPair<*>> = mutableListOf(DIFF_PLACE.with(place))

      val diffSettings = DiffSettings.getSettings(place)
      val defaultDiffSettings = DiffSettings.getDefaultSettings(place)

      val textSettings = TextDiffSettings.getSettings(place)
      val defaultTextSettings = TextDiffSettings.getDefaultSettings(place)

      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isEnableSyncScroll }, SYNC_SCROLL, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isEnableAligningChangesMode }, ALIGNED_CHANGES, data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.ignorePolicy }, IGNORE_POLICY, IGNORE_POLICY_VALUE, data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.highlightPolicy }, HIGHLIGHT_POLICY, HIGHLIGHT_POLICY_VALUE, data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.highlightingLevel }, SHOW_WARNINGS_POLICY, HIGHLIGHTING_LEVEL_VALUE,
                   data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.contextRange }, CONTEXT_RANGE, CONTEXT_RANGE_VALUE, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { !it.isExpandByDefault }, COLLAPSE_UNCHANGED, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isShowLineNumbers }, SHOW_LINE_NUMBERS, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isShowWhitespaces }, SHOW_WHITE_SPACES, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isShowIndentLines }, SHOW_INDENT_LINES, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isUseSoftWraps }, USE_SOFT_WRAPS, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isReadOnlyLock }, ENABLE_READ_LOCK, data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.breadcrumbsPlacement }, SHOW_BREADCRUMBS, BREADCRUMBS_PLACEMENT_VALUE,
                   data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isAutoApplyNonConflictedChanges }, MERGE_APPLY_NOT_CONFLICTED, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isEnableLstGutterMarkersInMerge }, MERGE_ENABLE_LST_MARKERS, data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isAutoResolveImportConflicts }, MERGE_AUTO_RESOLVE_IMPORT_CONFLICTS, data)
      addBoolIfDiffers(set, diffSettings, defaultDiffSettings, { isUnifiedToolDefault(it) }, USE_UNIFIED_DIFF, data)
    }

    val diffSettings = DiffSettings.getSettings(DiffPlaces.DEFAULT)
    val defaultDiffSettings = DiffSettings.getDefaultSettings(DiffPlaces.DEFAULT)
    addBoolIfDiffers(set, diffSettings, defaultDiffSettings, { it.isGoToNextFileOnNextDifference }, ITERATE_NEXT_FILE)

    val externalSettings = ExternalDiffSettings.instance
    val defaultExternalSettings = ExternalDiffSettings()
    addBoolIfDiffers(set, externalSettings, defaultExternalSettings, { it.isExternalToolsEnabled }, ENABLE_EXTERNAL_TOOLS)

    return set
  }

  private fun isUnifiedToolDefault(settings: DiffSettings): Boolean {
    val toolOrder = settings.diffToolsOrder
    val defaultToolIndex = toolOrder.indexOf(SimpleDiffTool::class.java.canonicalName)
    val unifiedToolIndex = toolOrder.indexOf(UnifiedDiffTool::class.java.canonicalName)
    if (unifiedToolIndex == -1) return false
    return defaultToolIndex == -1 || unifiedToolIndex < defaultToolIndex
  }
}
