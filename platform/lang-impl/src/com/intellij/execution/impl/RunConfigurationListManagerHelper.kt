/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.impl

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ObjectIntHashMap
import java.util.*

internal class RunConfigurationListManagerHelper(val manager: RunManagerImpl) {
  // template configurations are not included here
  val idToSettings = LinkedHashMap<String, RunnerAndConfigurationSettings>()

  private val customOrder = ObjectIntHashMap<String>()

  private var isCustomOrderApplied = true
    set(value) {
      if (field != value) {
        field = value
        if (!value) {
          immutableSortedSettingsList = null
        }
      }
    }

  @Volatile
  var immutableSortedSettingsList: List<RunnerAndConfigurationSettings>? = emptyList()

  fun setOrder(comparator: Comparator<RunnerAndConfigurationSettings>) {
    val sorted = idToSettings.values.filterTo(ArrayList(idToSettings.size)) { it.type !is UnknownConfigurationType }
    sorted.sortWith(comparator)
    customOrder.clear()
    customOrder.ensureCapacity(sorted.size)
    sorted.mapIndexed { index, settings -> customOrder.put(settings.uniqueID, index) }
    immutableSortedSettingsList = null
    isCustomOrderApplied = false
  }

  fun requestSort() {
    if (customOrder.isEmpty) {
      sortAlphabetically()
    }
    else {
      isCustomOrderApplied = false
    }
    immutableSortedSettingsList = null
  }

  fun setCustomOrder(order: List<String>) {
    customOrder.clear()
    customOrder.ensureCapacity(order.size)
    order.mapIndexed { index, id -> customOrder.put(id, index) }
  }

  private fun sortAlphabetically() {
    if (idToSettings.isEmpty()) {
      return
    }

    val list = idToSettings.values.sortedWith(Comparator { o1, o2 ->
      val temporary1 = o1.isTemporary
      val temporary2 = o2.isTemporary
      when {
        temporary1 == temporary2 -> o1.uniqueID.compareTo(o2.uniqueID)
        temporary1 -> 1
        else -> -1
      }
    })
    idToSettings.clear()
    for (settings in list) {
      idToSettings.put(settings.uniqueID, settings)
    }
  }

  fun buildImmutableSortedSettingsList(): List<RunnerAndConfigurationSettings> {
    immutableSortedSettingsList?.let {
      return it
    }

    if (idToSettings.isEmpty()) {
      immutableSortedSettingsList = emptyList()
      return immutableSortedSettingsList!!
    }

    // IDEA-63663 Sort run configurations alphabetically if clean checkout
    if (!isCustomOrderApplied && !customOrder.isEmpty) {
      val list = idToSettings.values.toTypedArray()
      val folderNames = SmartList<String>()
      for (settings in list) {
        val folderName = settings.folderName
        if (folderName != null && !folderNames.contains(folderName)) {
          folderNames.add(folderName)
        }
      }

      folderNames.sortWith(NaturalComparator.INSTANCE)
      folderNames.add(null)

      list.sortWith(Comparator { o1, o2 ->
        if (o1.folderName != o2.folderName) {
          val i1 = folderNames.indexOf(o1.folderName)
          val i2 = folderNames.indexOf(o2.folderName)
          if (i1 != i2) {
            return@Comparator i1 - i2
          }
        }

        val temporary1 = o1.isTemporary
        val temporary2 = o2.isTemporary
        when {
          temporary1 == temporary2 -> {
            val index1 = customOrder.get(o1.uniqueID)
            val index2 = customOrder.get(o2.uniqueID)
            if (index1 == -1 && index2 == -1) {
              o1.name.compareTo(o2.name)
            }
            else {
              index1 - index2
            }
          }
          temporary1 -> 1
          else -> -1
        }
      })

      isCustomOrderApplied = true
      idToSettings.clear()
      for (settings in list) {
        idToSettings.put(settings.uniqueID, settings)
      }
    }

    val result = Collections.unmodifiableList(idToSettings.values.toList())
    immutableSortedSettingsList = result
    return result
  }

  fun afterMakeStable() {
    immutableSortedSettingsList = null
    if (!customOrder.isEmpty) {
      isCustomOrderApplied = false
    }
  }

  fun checkIfDependenciesAreStable(configuration: RunConfiguration, list: List<RunnerAndConfigurationSettings>) {
    for (runTask in configuration.beforeRunTasks) {
      val runTaskSettings = (runTask as? RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)?.settings

      if (runTaskSettings?.isTemporary == true) {
        manager.makeStable(runTaskSettings)
        checkIfDependenciesAreStable(runTaskSettings.configuration, list)
      }
    }

    if (configuration is CompoundRunConfiguration) {
      val children = configuration.getConfigurationsWithTargets(manager)
      for (otherSettings in list) {
        if (!otherSettings.isTemporary) {
          continue
        }

        val otherConfiguration = otherSettings.configuration
        if (otherConfiguration === configuration) {
          continue
        }

        if (ContainerUtil.containsIdentity(children.keys, otherConfiguration)) {
          if (otherSettings.isTemporary) {
            manager.makeStable(otherSettings)
            checkIfDependenciesAreStable(otherConfiguration, list)
          }
        }
      }
    }
  }
}