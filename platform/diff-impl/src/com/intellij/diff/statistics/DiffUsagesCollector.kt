/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.statistics

import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.tools.fragmented.UnifiedDiffTool
import com.intellij.diff.tools.simple.SimpleDiffTool
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.util.DiffPlaces
import com.intellij.internal.statistic.UsagesCollector
import com.intellij.internal.statistic.beans.GroupDescriptor
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.util.containers.ContainerUtil

class DiffUsagesCollector : UsagesCollector() {
  companion object {
    val ID = GroupDescriptor.create("Diff")
  }

  override fun getGroupId(): GroupDescriptor {
    return ID
  }

  override fun getUsages(): Set<UsageDescriptor> {
    val usages = ContainerUtil.newHashSet<UsageDescriptor>()

    processUsages(DiffPlaces.DEFAULT, usages)
    processUsages(DiffPlaces.CHANGES_VIEW, usages)
    processUsages(DiffPlaces.COMMIT_DIALOG, usages)

    val diffSettings = DiffSettingsHolder.getInstance().getSettings(null)
    usages.add(UsageDescriptor("diff.DiffSettings.Default.IterateNextFile", if (diffSettings.isGoToNextFileOnNextDifference) 1 else 0))

    return usages
  }

  private fun processUsages(place: String, usages: MutableSet<UsageDescriptor>) {
    val diffSettings = DiffSettingsHolder.getInstance().getSettings(place)
    val textSettings = TextDiffSettingsHolder.getInstance().getSettings(place)

    usages.add(UsageDescriptor("diff.TextDiffSettings.Default.IgnorePolicy." + textSettings.ignorePolicy.name, 1))
    usages.add(UsageDescriptor("diff.TextDiffSettings.Default.HighlightPolicy." + textSettings.highlightPolicy.name, 1))
    usages.add(UsageDescriptor("diff.TextDiffSettings.Default.ExpandByDefault", if (textSettings.isExpandByDefault) 1 else 0))

    val toolOrder = diffSettings.diffToolsOrder
    val defaultToolIndex = ContainerUtil.indexOf(toolOrder, SimpleDiffTool::class.java.canonicalName)
    val unifiedToolIndex = ContainerUtil.indexOf(toolOrder, UnifiedDiffTool::class.java.canonicalName)
    val isUnifiedDefault = unifiedToolIndex != -1 && unifiedToolIndex < defaultToolIndex
    usages.add(UsageDescriptor("diff.DiffSettings.Default.isUnifiedTool", if (isUnifiedDefault) 1 else 0))
  }
}
