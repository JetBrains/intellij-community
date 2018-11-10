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
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getEnumUsage
import java.util.*

class DiffUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId(): String {
    return "statistics.vcs.diff"
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
      val textSettings = TextDiffSettings.getSettings(place)

      usages.add(getEnumUsage("ignore.policy.$place", textSettings.ignorePolicy))
      usages.add(getEnumUsage("highlight.policy.$place", textSettings.highlightPolicy))
      usages.add(getEnumUsage("show.warnings.policy.$place", textSettings.highlightingLevel))
      usages.add(getBooleanUsage("collapse.unchanged.$place", !textSettings.isExpandByDefault))
      usages.add(getBooleanUsage("show.line.numbers.$place", textSettings.isShowLineNumbers))
      usages.add(getBooleanUsage("use.soft.wraps.$place", textSettings.isUseSoftWraps))
      usages.add(getBooleanUsage("use.unified.diff.$place", isUnifiedToolDefault(diffSettings)))

      if (place == DiffPlaces.COMMIT_DIALOG) {
        usages.add(getBooleanUsage("enable.read.lock.$place", textSettings.isReadOnlyLock))
      }
    }

    val diffSettings = DiffSettings.getSettings(null)
    usages.add(getBooleanUsage("iterate.next.file", diffSettings.isGoToNextFileOnNextDifference))

    val externalSettings = ExternalDiffSettings.getInstance()
    usages.add(getBooleanUsage("external.diff", externalSettings.isDiffEnabled))
    usages.add(getBooleanUsage("external.diff.default", externalSettings.isDiffEnabled && externalSettings.isDiffDefault))
    usages.add(getBooleanUsage("external.merge", externalSettings.isMergeEnabled))

    return usages
  }

  private fun isUnifiedToolDefault(settings: DiffSettings): Boolean {
    val toolOrder = settings.diffToolsOrder
    val defaultToolIndex = toolOrder.indexOf(SimpleDiffTool::class.java.canonicalName)
    val unifiedToolIndex = toolOrder.indexOf(UnifiedDiffTool::class.java.canonicalName)
    if (unifiedToolIndex == -1) return false
    return defaultToolIndex == -1 || unifiedToolIndex < defaultToolIndex
  }
}
