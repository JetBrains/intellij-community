// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

@ApiStatus.Experimental
class BuildViewGroupingSupport(source: Any) {
  private val groupingConfig: MutableMap<String, Boolean> = mutableMapOf(
    SOURCE_ROOT_GROUPING to PropertiesComponent.getInstance().getBoolean("build.view.grouping.by.$SOURCE_ROOT_GROUPING"))

  private val changeSupport: PropertyChangeSupport = PropertyChangeSupport(source)
  fun getGroupedId(event: BuildEvent): Any = if (event is MessageEvent) GroupedId(event.id, groupingConfig.values.toList()) else event.id
  fun getAllGroupedIds(event: BuildEvent): Collection<Any> {
    if (event is MessageEvent) {
      return listOf(GroupedId(event.id, listOf(true)),
                    GroupedId(event.id, listOf(false)))
    }
    else return listOf(event.id)
  }

  operator fun get(groupingKey: String): Boolean {
    return groupingConfig[groupingKey] ?: false
  }

  operator fun set(groupingKey: String, state: Boolean) {
    if (groupingConfig[groupingKey] != state) {
      val oldGrouping = groupingConfig.toMap()
      groupingConfig[groupingKey] = state
      changeSupport.firePropertyChange(PROP_GROUPING_KEYS, oldGrouping, groupingConfig)
      PropertiesComponent.getInstance().setValue("build.view.grouping.by.$groupingKey", state)
    }
  }

  fun isAvailable(groupingKey: String): Boolean? {
    return groupingConfig.containsKey(groupingKey)
  }

  fun addPropertyChangeListener(listener: PropertyChangeListener): Unit = changeSupport.addPropertyChangeListener(listener)

  companion object {
    @JvmField
    val KEY: DataKey<BuildViewGroupingSupport> = DataKey.create<BuildViewGroupingSupport>("BuildView.GroupingSupport")
    const val PROP_GROUPING_KEYS: String = "BuildGroupingKeys"
    const val SOURCE_ROOT_GROUPING: String = "source.root"
  }

  data class GroupedId(val id: Any, val groupingConfig: Collection<Boolean>)
}

