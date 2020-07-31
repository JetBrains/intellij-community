// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.ActivityImpl
import com.intellij.diagnostic.ThreadNameManager
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

// events must be already sorted by time
internal fun computeOwnTime(allEvents: List<ActivityImpl>, threadNameManager: ThreadNameManager): Object2LongMap<ActivityImpl> {
  val ownDurations = Object2LongOpenHashMap<ActivityImpl>()
  ownDurations.defaultReturnValue(-1)

  val threadToList = Object2ObjectOpenHashMap<String, MutableList<ActivityImpl>>()
  for (event in allEvents) {
    threadToList.getOrPut(threadNameManager.getThreadName(event)) { mutableListOf() }.add(event)
  }

  val respectedItems = mutableListOf<ActivityImpl>()

  for (list in threadToList.values) {
    for ((index, item) in list.withIndex()) {
      if (item.category == ActivityCategory.SERVICE_WAITING) {
        continue
      }

      val totalDuration = item.end - item.start
      var ownDuration = totalDuration
      respectedItems.clear()

      if (index > 0 && list.get(index - 1).start > item.start) {
        StartUpPerformanceReporter.LOG.error("prev ${list.get(index - 1).name} start > ${item.name}")
      }

      for (i in (index + 1) until list.size) {
        val otherItem = list.get(i)
        if (otherItem.end > item.end) {
          break
        }

        if (isInclusive(otherItem, item) && !respectedItems.any { isInclusive(otherItem, it) }) {
          ownDuration -= otherItem.end - otherItem.start
          respectedItems.add(otherItem)
        }
      }

      if (totalDuration != ownDuration) {
        ownDurations.put(item, ownDuration)
      }
    }
  }

  return ownDurations
}

private fun isInclusive(otherItem: ActivityImpl, item: ActivityImpl): Boolean {
  return otherItem.start >= item.start && otherItem.end <= item.end
}