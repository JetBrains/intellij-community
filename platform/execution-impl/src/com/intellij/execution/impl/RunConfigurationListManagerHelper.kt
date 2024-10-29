// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.execution.impl

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.compound.CompoundRunConfiguration
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.openapi.util.text.NaturalComparator
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jdom.Element

internal class RunConfigurationListManagerHelper(private val manager: RunManagerImpl) {
  // template configurations are not included here
  @JvmField
  val idToSettings: LinkedHashMap<String, RunnerAndConfigurationSettings> = LinkedHashMap()

  private val customOrder = Object2IntOpenHashMap<String>()

  init {
    customOrder.defaultReturnValue(-1)
  }

  private var isSorted = false
    set(value) {
      if (field != value) {
        field = value
        if (!value) {
          immutableSortedSettingsList = null
        }
      }
    }

  @Volatile
  @JvmField
  var immutableSortedSettingsList: List<RunnerAndConfigurationSettings>? = emptyList()

  fun setOrder(comparator: Comparator<RunnerAndConfigurationSettings>, isApplyAdditionalSortByTypeAndGroup: Boolean) {
    val sorted = idToSettings.values.toTypedArray()
    if (isApplyAdditionalSortByTypeAndGroup) {
      val folderNames = getSortedFolderNames(idToSettings.values)
      sorted.sortWith(compareByTypeAndFolderAndCustomComparator(folderNames, comparator))
    }
    else {
      sorted.sortWith(comparator)
    }
    customOrder.clear()
    sorted.mapIndexed { index, settings -> customOrder.put(settings.uniqueID, index) }
    immutableSortedSettingsList = null
    isSorted = true
    idToSettings.clear()
    for (settings in sorted) {
      idToSettings.put(settings.uniqueID, settings)
    }
  }

  private fun compareByTypeAndFolderAndCustomComparator(
    folderNames: List<String?>,
    comparator: Comparator<RunnerAndConfigurationSettings>,
  ): Comparator<RunnerAndConfigurationSettings> {
    return Comparator { o1, o2 ->
      val type1 = o1.type
      val type2 = o2.type
      if (type1 !== type2) {
        return@Comparator compareTypesForUi(type1, type2)
      }

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
          comparator.compare(o1, o2)
        }
        temporary1 -> 1
        else -> -1
      }
    }
  }

  fun updateConfigurationId(oldId: String, newId: String) {
    if (customOrder.containsKey(oldId)) {
      customOrder.put(newId, customOrder.removeInt(oldId))
    }
  }

  fun requestSort() {
    isSorted = false
    immutableSortedSettingsList = null
  }

  fun writeOrder(parent: Element) {
    if (customOrder.isEmpty()) {
      return
    }

    val listElement = Element("list")
    idToSettings.values.managedOnly().forEach {
      listElement.addContent(Element("item").setAttribute("itemvalue", it.uniqueID))
    }

    if (!listElement.isEmpty) {
      parent.addContent(listElement)
    }
  }

  fun readCustomOrder(element: Element) {
    element.getChild("list")?.let { listElement ->
      var order = 0
      customOrder.clear()
      for (child in listElement.getChildren("item")) {
        child.getAttributeValue("itemvalue")?.let {
          customOrder.put(it, order++)
        }
      }
    }

    requestSort()
  }

  private fun sortAlphabetically() {
    if (idToSettings.isEmpty()) {
      return
    }

    val folderNames = getSortedFolderNames(idToSettings.values)
    val list = idToSettings.values.sortedWith(compareByTypeAndFolderAndCustomComparator(folderNames) { o1, o2 ->
      NaturalComparator.INSTANCE.compare(o1.name, o2.name)
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
    if (!isSorted) {
      if (customOrder.isEmpty()) {
        sortAlphabetically()
      }
      else {
        doCustomSort()
      }
    }

    val result = java.util.List.copyOf(idToSettings.values)
    immutableSortedSettingsList = result
    return result
  }

  private fun doCustomSort() {
    val list = idToSettings.values.toTypedArray()
    val folderNames = getCustomOrderedFolderNames(idToSettings.values, customOrder)
    // customOrder maybe outdated (order specified not all RC), so, base sort by type and folder is applied
    list.sortWith(compareByTypeAndFolderAndCustomComparator(folderNames) { o1, o2 ->
      val index1 = customOrder.getInt(o1.uniqueID)
      val index2 = customOrder.getInt(o2.uniqueID)
      if (index1 == -1 && index2 == -1) {
        o1.name.compareTo(o2.name)
      }
      else {
        index1 - index2
      }
    })

    isSorted = true
    idToSettings.clear()
    for (settings in list) {
      idToSettings.put(settings.uniqueID, settings)
    }
  }

  fun afterMakeStable() {
    immutableSortedSettingsList = null
    if (!customOrder.isEmpty()) {
      isSorted = false
    }
  }

  fun checkIfDependenciesAreStable(configuration: RunConfiguration, list: List<RunnerAndConfigurationSettings>) {
    for (runTask in configuration.beforeRunTasks) {
      val runTaskSettings = (runTask as? RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)?.getSettings(manager)
      if (runTaskSettings?.isTemporary == true) {
        manager.makeStable(runTaskSettings)
        checkIfDependenciesAreStable(runTaskSettings.configuration, list)
      }
    }

    if (configuration !is CompoundRunConfiguration) {
      return
    }

    val children = configuration.getConfigurationsWithTargets(manager)
    for (otherSettings in list) {
      if (!otherSettings.isTemporary) {
        continue
      }

      val otherConfiguration = otherSettings.configuration
      if (otherConfiguration === configuration) {
        continue
      }

      if (children.keys.any { it === otherConfiguration } && otherSettings.isTemporary) {
        manager.makeStable(otherSettings)
        checkIfDependenciesAreStable(otherConfiguration, list)
      }
    }
  }
}

private fun getSortedFolderNames(list: Collection<RunnerAndConfigurationSettings>): List<String?> {
  val result = ArrayList<String?>()
  for (settings in list) {
    val folderName = settings.folderName
    if (folderName != null && !result.contains(folderName)) {
      result.add(folderName)
    }
  }

  result.sortWith(NaturalComparator.INSTANCE)
  result.add(null)
  return result
}

private fun getCustomOrderedFolderNames(
  list: Collection<RunnerAndConfigurationSettings>,
  customOrder: Object2IntMap<String>,
): List<String?> {
  val folderNamesWithOrder = ArrayList<Pair<String, Int>>()
  for (settings in list) {
    val folderName = settings.folderName
    if (folderName != null) {
      val order = customOrder.getInt(settings.uniqueID)
      folderNamesWithOrder.add(Pair(folderName, order))
    }
  }
  folderNamesWithOrder.sortBy { it.second }
  return folderNamesWithOrder.map { it.first }.distinct().plus(null)
}

internal fun Collection<RunnerAndConfigurationSettings>.managedOnly(): Sequence<RunnerAndConfigurationSettings> {
  return asSequence().filter { it.type.isManaged }
}

internal fun compareTypesForUi(type1: ConfigurationType, type2: ConfigurationType): Int {
  return when {
    type1 === type2 -> 0
    type1 === UnknownConfigurationType.getInstance() -> 1
    type2 === UnknownConfigurationType.getInstance() -> -1
    else -> NaturalComparator.INSTANCE.compare(type1.displayName, type2.displayName)
  }
}
