// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.statistics

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.util.DiffPlaces
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.addIfDiffers
import java.util.*

class DiffUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId(): String {
    return "vcs.diff"
  }

  override fun getUsages(): MutableSet<UsageDescriptor> {
    val usages = HashSet<UsageDescriptor>()

    val places = listOf(DiffPlaces.DEFAULT,
                        DiffPlaces.CHANGES_VIEW,
                        DiffPlaces.VCS_LOG_VIEW,
                        DiffPlaces.COMMIT_DIALOG,
                        DiffPlaces.MERGE,
                        DiffPlaces.TESTS_FAILED_ASSERTIONS)

    for (place in places) {
      val diffSettings = DiffSettings.getSettings(place)
      val defaultDiffSettings = DiffSettings.getDefaultSettings(place)

      val textSettings = TextDiffSettings.getSettings(place)
      val defaultTextSettings = TextDiffSettings.getDefaultSettings(place)

      addIfDiffers(usages, textSettings, defaultTextSettings, { it.ignorePolicy }, { "ignore.policy.$place.$it" })
      addIfDiffers(usages, textSettings, defaultTextSettings, { it.highlightPolicy }, { "highlight.policy.$place.$it" })
      addIfDiffers(usages, textSettings, defaultTextSettings, { it.highlightingLevel }, { "show.warnings.policy.$place.$it" })
      addBoolIfDiffers(usages, textSettings, defaultTextSettings, { !it.isExpandByDefault }, "collapse.unchanged.$place")
      addBoolIfDiffers(usages, textSettings, defaultTextSettings, { it.isShowLineNumbers }, "show.line.numbers.$place")
      addBoolIfDiffers(usages, textSettings, defaultTextSettings, { it.isUseSoftWraps }, "use.soft.wraps.$place")
      addBoolIfDiffers(usages, diffSettings, defaultDiffSettings, { isUnifiedToolDefault(it) }, "use.unified.diff.$place")

      addBoolIfDiffers(usages, textSettings, defaultTextSettings, { it.isReadOnlyLock }, "enable.read.lock.$place")
    }

    val diffSettings = DiffSettings.getSettings(DiffPlaces.DEFAULT)
    val defaultDiffSettings = DiffSettings.getDefaultSettings(DiffPlaces.DEFAULT)
    addBoolIfDiffers(usages, diffSettings, defaultDiffSettings, { it.isGoToNextFileOnNextDifference }, "iterate.next.file")

    val externalSettings = ExternalDiffSettings.instance
    val defaultExternalSettings = ExternalDiffSettings()
    addBoolIfDiffers(usages, externalSettings, defaultExternalSettings, { it.isDiffEnabled }, "external.diff.enabled")
    addBoolIfDiffers(usages, externalSettings, defaultExternalSettings, { it.isDiffEnabled && it.isDiffDefault }, "external.diff.default")
    addBoolIfDiffers(usages, externalSettings, defaultExternalSettings, { it.isMergeEnabled }, "external.merge.enabled")

    return usages
  }

  private fun isUnifiedToolDefault(settings: DiffSettings): Boolean {
    val toolOrder = settings.diffToolsOrder
    val defaultToolIndex = toolOrder.indexOf(SimpleDiffTool::class.java.canonicalName)
    val unifiedToolIndex = toolOrder.indexOf(UnifiedDiffTool::class.java.canonicalName)
    if (unifiedToolIndex == -1) return false
    return defaultToolIndex == -1 || unifiedToolIndex < defaultToolIndex
  }

  private fun <T> addBoolIfDiffers(set: MutableSet<in UsageDescriptor>, settingsBean: T, defaultSettingsBean: T,
                                   valueFunction: Function1<T, Boolean>, featureId: String) {
    addIfDiffers(set, settingsBean, defaultSettingsBean, valueFunction) { if (it) featureId else "$featureId.disabled" }
  }
}
