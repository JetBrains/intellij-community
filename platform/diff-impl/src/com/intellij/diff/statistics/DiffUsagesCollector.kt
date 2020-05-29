// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.statistics

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.DiffPlaces
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addMetricsIfDiffers
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import java.util.*

class DiffUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId(): String = "vcs.diff"
  override fun getVersion(): Int = 4

  override fun getMetrics(): Set<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val places = listOf(DiffPlaces.DEFAULT,
                        DiffPlaces.CHANGES_VIEW,
                        DiffPlaces.VCS_LOG_VIEW,
                        DiffPlaces.VCS_FILE_HISTORY_VIEW,
                        DiffPlaces.COMMIT_DIALOG,
                        DiffPlaces.MERGE,
                        DiffPlaces.TESTS_FAILED_ASSERTIONS)

    for (place in places) {
      val data = FeatureUsageData().addData("diff_place", place)

      val diffSettings = DiffSettings.getSettings(place)
      val defaultDiffSettings = DiffSettings.getDefaultSettings(place)

      val textSettings = TextDiffSettings.getSettings(place)
      val defaultTextSettings = TextDiffSettings.getDefaultSettings(place)

      addMetricsIfDiffers(set, textSettings, defaultTextSettings, data) {
        addBool("sync.scroll" ) { it.isEnableSyncScroll }
        add("ignore.policy") { it.ignorePolicy }
        add("highlight.policy") { it.highlightPolicy }
        add("show.warnings.policy") { it.highlightingLevel }
        add("context.range") { it.contextRange }
        addBool("collapse.unchanged") { !it.isExpandByDefault }
        addBool("show.line.numbers") { it.isShowLineNumbers }
        addBool("show.white.spaces") { it.isShowWhitespaces }
        addBool("show.indent.lines") { it.isShowIndentLines }
        addBool("use.soft.wraps") { it.isUseSoftWraps }
        addBool("enable.read.lock") { it.isReadOnlyLock }
        add("show.breadcrumbs") { it.breadcrumbsPlacement }
        addBool("merge.apply.non.conflicted") { it.isAutoApplyNonConflictedChanges }
        addBool("merge.enable.lst.markers") { it.isEnableLstGutterMarkersInMerge }
      }
      addBoolIfDiffers(set, diffSettings, defaultDiffSettings, { isUnifiedToolDefault(it) }, "use.unified.diff", data)
    }

    val diffSettings = DiffSettings.getSettings(DiffPlaces.DEFAULT)
    val defaultDiffSettings = DiffSettings.getDefaultSettings(DiffPlaces.DEFAULT)
    addBoolIfDiffers(set, diffSettings, defaultDiffSettings, { it.isGoToNextFileOnNextDifference }, "iterate.next.file")

    val externalSettings = ExternalDiffSettings.instance
    val defaultExternalSettings = ExternalDiffSettings()
    addBoolIfDiffers(set, externalSettings, defaultExternalSettings, { it.isDiffEnabled }, "use.external.diff")
    addBoolIfDiffers(set, externalSettings, defaultExternalSettings, { it.isDiffEnabled && it.isDiffDefault }, "use.external.diff.by.default")
    addBoolIfDiffers(set, externalSettings, defaultExternalSettings, { it.isMergeEnabled }, "use.external.merge")

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
