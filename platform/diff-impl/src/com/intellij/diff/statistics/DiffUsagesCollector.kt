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
    val VERSION = 1
  }

  override fun getGroupId(): GroupDescriptor {
    return ID
  }

  override fun getUsages(): Set<UsageDescriptor> {
    val usages: MutableMap<String, Int> = ContainerUtil.newHashMap()
    usages["Total"] = 1


    listOf(DiffPlaces.DEFAULT, DiffPlaces.CHANGES_VIEW, DiffPlaces.COMMIT_DIALOG).forEach { place ->
      val diffSettings = DiffSettingsHolder.getInstance().getSettings(place)
      val textSettings = TextDiffSettingsHolder.getInstance().getSettings(place)

      usages.increment("TextDiffSettings.IgnorePolicy", textSettings.ignorePolicy)
      usages.increment("TextDiffSettings.HighlightPolicy", textSettings.highlightPolicy)
      usages.increment("TextDiffSettings.ExpandByDefault", textSettings.isExpandByDefault)
      usages.increment("DiffSettings.isUnifiedTool", isUnifiedToolDefault(diffSettings))
    }


    val diffSettings = DiffSettingsHolder.getInstance().getSettings(null)
    usages.increment("DiffSettings.IterateNextFile", diffSettings.isGoToNextFileOnNextDifference)


    return usages.map { it -> UsageDescriptor("diff.$VERSION.${it.key}", it.value) }.toSet()
  }

  private fun isUnifiedToolDefault(settings: DiffSettingsHolder.DiffSettings): Boolean {
    val toolOrder = settings.diffToolsOrder
    val defaultToolIndex = ContainerUtil.indexOf(toolOrder, SimpleDiffTool::class.java.canonicalName)
    val unifiedToolIndex = ContainerUtil.indexOf(toolOrder, UnifiedDiffTool::class.java.canonicalName)
    return unifiedToolIndex != -1 && unifiedToolIndex < defaultToolIndex
  }

  private fun MutableMap<String, Int>.increment(key: String) {
    put(key, (get(key) ?: 0) + 1)
  }

  private fun MutableMap<String, Int>.increment(prefix: String, suffix: Enum<*>) {
    increment("$prefix.${suffix.name}")
  }

  private fun MutableMap<String, Int>.increment(key: String, condition: Boolean) {
    if (condition) increment(key)
  }
}
