// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.ide.plugins.DynamicPluginEnabler
import com.intellij.ide.plugins.PluginStateManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentImpl.Companion.OPTION_ID_FREE_GROUP
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentImpl.Companion.TOTAL_NUMBER_OF_GROUPS

/**
 * This storage is used to ensure that option groups are assigned properly.
 * It maintains a map from a group's number to an assigned option id.
 * It uses a special id to mark free groups.
 *
 * At the start, the map is initialized with available options.
 * After that, for each event of plugin enabling/disabling/installing/uninstalling and after startup,
 * it checks new available options and puts its id into the map.
 * Ids of options that become not available are not removed from the map.
 * This is to avoid mixing different options with the user and
 * handle a case when a user enables and disables a plugin several times.
 *
 * @see com.intellij.platform.experiment.ab.impl.experiment.ABExperiment.OPTION_ID_FREE_GROUP
 */
@Service(Service.Level.APP)
@State(
  name = "ABExperimentGroupStorageService",
  storages = [Storage("ABExperimentGroupStorageService.xml", roamingType = RoamingType.DISABLED)]
)
internal class ABExperimentGroupStorageService : PersistentStateComponent<ABExperimentGroupStorage> {

  companion object {
    private val LOG = logger<ABExperimentGroupStorageService>()

    fun getUserExperimentOptionId(userGroupNumber: Int): ABExperimentOptionId {
      val experimentOptionIdText = service<ABExperimentGroupStorageService>().state.groupNumberToExperimentOptionId[userGroupNumber]!!
      return ABExperimentOptionId(experimentOptionIdText)
    }
  }

  private lateinit var myState: ABExperimentGroupStorage

  override fun getState(): ABExperimentGroupStorage {
    return myState
  }

  override fun loadState(state: ABExperimentGroupStorage) {
    myState = state
  }

  override fun noStateLoaded() {
    myState = ABExperimentGroupStorage(getInitialGroupToOptionState())
  }

  override fun initializeComponent() {
    val tracker = ABExperimentPluginTracker()
    PluginStateManager.addStateListener(tracker)
    DynamicPluginEnabler.addPluginStateChangedListener(tracker)

    setupNewPluginABExperimentOptions()
  }

  internal fun setupNewPluginABExperimentOptions() {
    val groupNumberToExperimentOptionId = myState.groupNumberToExperimentOptionId
    LOG.debug { "State BEFORE update is: $groupNumberToExperimentOptionId" }

    val optionBeans = ABExperimentImpl.getJbABExperimentOptionBeanList()
    val usedOptionIds = groupNumberToExperimentOptionId.values.toSet()
    val newOptionBeans = optionBeans.filter { it.instance.id.value !in usedOptionIds }

    if (newOptionBeans.isEmpty()) {
      return
    }

    val isPopularIDE = ABExperimentImpl.isPopularIDE()

    for (newOptionBean in newOptionBeans) {
      val newOption = newOptionBean.instance
      val groupCount = newOption.getGroupSizeForIde(isPopularIDE).groupCount
      for (i in 0 until groupCount) {
        val freeGroupKey = groupNumberToExperimentOptionId.entries.find { entry ->
          entry.value == OPTION_ID_FREE_GROUP.value
        }?.key

        if (freeGroupKey == null) {
          LOG.error("There is no available groups for option ${newOption.id} from plugin " +
                    newOptionBean.pluginDescriptor.pluginId.idString)
          return
        }

        LOG.debug { "Assign experiment option ${newOption.id} to group $freeGroupKey." }

        groupNumberToExperimentOptionId[freeGroupKey] = newOption.id.value
      }
    }
    LOG.debug { "State AFTER update is: $groupNumberToExperimentOptionId" }
  }

  private fun getInitialGroupToOptionState(): MutableMap<Int, String> {
    val initialGroupNumberToExperimentOptionId = (0.rangeUntil(TOTAL_NUMBER_OF_GROUPS).associateWith {
      OPTION_ID_FREE_GROUP.value
    }).toMutableMap()

    val isPopularIDE = ABExperimentImpl.isPopularIDE()
    val options = ABExperimentImpl.getJbABExperimentOptionList().sortedBy { it.id.value }

    var counter = 0

    for (option in options) {
      val optionGroupsCount = option.getGroupSizeForIde(isPopularIDE).groupCount
      for (groupNumber in counter.rangeUntil(counter + optionGroupsCount)) {
        LOG.debug { "Assign experiment option ${option.id} to group $groupNumber." }
        initialGroupNumberToExperimentOptionId[groupNumber] = option.id.value
      }
      counter += optionGroupsCount
    }

    LOG.debug { "Initial state of group to option map is: $initialGroupNumberToExperimentOptionId" }

    return initialGroupNumberToExperimentOptionId
  }
}

internal data class ABExperimentGroupStorage(val groupNumberToExperimentOptionId: MutableMap<Int, String>)