// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.statistics

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.DiffPlaces
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.beans.addIfDiffers
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import java.util.*

class DiffUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId(): String = "vcs.diff"
  override fun getVersion(): Int = 2

  override fun getMetrics(): MutableSet<MetricEvent> {
    val set = HashSet<MetricEvent>()

    val places = listOf(DiffPlaces.DEFAULT,
                        DiffPlaces.CHANGES_VIEW,
                        DiffPlaces.VCS_LOG_VIEW,
                        DiffPlaces.COMMIT_DIALOG,
                        DiffPlaces.MERGE,
                        DiffPlaces.TESTS_FAILED_ASSERTIONS)

    for (place in places) {
      val data = FeatureUsageData().addData("diff_place", place)

      val diffSettings = DiffSettings.getSettings(place)
      val defaultDiffSettings = DiffSettings.getDefaultSettings(place)

      val textSettings = TextDiffSettings.getSettings(place)
      val defaultTextSettings = TextDiffSettings.getDefaultSettings(place)

      addIfDiffers(set, textSettings, defaultTextSettings, { it.ignorePolicy }, "ignore.policy", data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.highlightPolicy }, "highlight.policy", data)
      addIfDiffers(set, textSettings, defaultTextSettings, { it.highlightingLevel }, "show.warnings.policy", data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { !it.isExpandByDefault }, "collapse.unchanged", data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isShowLineNumbers }, "show.line.numbers", data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isUseSoftWraps }, "use.soft.wraps", data)
      addBoolIfDiffers(set, diffSettings, defaultDiffSettings, { isUnifiedToolDefault(it) }, "use.unified.diff", data)
      addBoolIfDiffers(set, textSettings, defaultTextSettings, { it.isReadOnlyLock }, "enable.read.lock", data)
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
