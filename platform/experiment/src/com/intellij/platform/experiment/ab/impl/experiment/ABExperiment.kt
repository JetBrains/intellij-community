// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.experiment.ab.impl.option.ABExperimentControlOption
import com.intellij.util.MathUtil
import com.intellij.util.PlatformUtils

fun getABExperimentInstance(): ABExperiment {
  return ApplicationManager.getApplication().service<ABExperiment>()
}

@Service
class ABExperiment {

  companion object {
    private val AB_EXPERIMENTAL_OPTION_EP = ExtensionPointName<ABExperimentOption>("com.intellij.experiment.abExperimentOption")
    private val LOG = logger<ABExperiment>()

    private const val DEVICE_ID_PURPOSE = "A/B Experiment"
    private const val DEVICE_ID_SALT = "ab experiment salt"
    private const val TOTAL_NUMBER_OF_BUCKETS = 1024

    internal fun getJbABExperimentOptionList(): List<ABExperimentOption> {
      return AB_EXPERIMENTAL_OPTION_EP.extensionList.filter {
        val pluginDescriptor = it.getPluginDescriptor()
        val pluginInfo = getPluginInfoByDescriptor(pluginDescriptor)
        pluginInfo.isDevelopedByJetBrains()
      }
    }
  }

  fun isExperimentOptionEnabled(experimentOptionClass: Class<out ABExperimentOption>): Boolean {
    return experimentOptionClass.isInstance(getUserExperimentOption())
  }

  fun isControlExperimentOptionEnabled(): Boolean {
    return isExperimentOptionEnabled(ABExperimentControlOption::class.java)
  }

  fun getUserExperimentOption(): ABExperimentOption {
    val manualOptionId = Registry.stringValue("platform.experiment.ab.manual.option")
    if (manualOptionId.isNotBlank()) {

      LOG.debug { "Use manual option id from Registry. Registry key value is: $manualOptionId" }

      val manualOption = getJbABExperimentOptionList().find { it.id == manualOptionId }
      if (manualOption != null) {
        LOG.debug { "Found manual option is: $manualOption" }
        return manualOption
      }
      else {
        LOG.debug { "Manual option with id $manualOptionId not found. Returning control option." }
        return getExperimentControlOption()
      }
    }

    val isPopularIde = isPopularIDE()
    val orderedOptionList = getJbABExperimentOptionList().sortedBy { it.id }
    return computeUserABOptionByGroupCounts(orderedOptionList.map { it.getGroupCountForIde(isPopularIde) to it })
  }

  internal fun getUserGroupNumber(): Int? {
    val bucket = getUserBucket()
    val totalNumberOfGroups = getTotalNumberOfGroups()
    if (TOTAL_NUMBER_OF_BUCKETS < totalNumberOfGroups) {
      LOG.error("Number of buckets is less than number of groups. " +
                "Please revise all experiment options and adjust their group counts.")
      return null
    }

    val experimentGroup = bucket % totalNumberOfGroups
    return experimentGroup
  }

  internal fun getUserBucket(): Int {
    val deviceId = LOG.runAndLogException {
      MachineIdManager.getAnonymizedMachineId(DEVICE_ID_PURPOSE, DEVICE_ID_SALT)
    }

    return MathUtil.nonNegativeAbs(deviceId.hashCode()) % TOTAL_NUMBER_OF_BUCKETS
  }

  private fun computeUserABOptionByGroupCounts(groupCountToOption: List<Pair<Int, ABExperimentOption>>): ABExperimentOption {
    val groupNumber = getUserGroupNumber() ?: return getExperimentControlOption()

    var counter = -1
    for ((groupCount, option) in groupCountToOption) {
      LOG.debug {
        "User group number is: $groupNumber. " +
                "Option group count is: $groupCount. " +
        "Sum of previous group counts is: $counter."
      }
      if (counter + groupCount > groupNumber) {
        LOG.debug { "User group belongs to $option option." }
        return option
      }

      counter += groupCount
    }

    LOG.error("User group is not in range of available groups. " +
              "User group is: $groupNumber. " +
              "Group count to option mapping is: $groupCountToOption")

    return getExperimentControlOption()
  }

  private fun getTotalNumberOfGroups(): Int {
    val isPopularIde = isPopularIDE()
    val totalNumberOfGroups = getJbABExperimentOptionList().sumOf { it.getGroupCountForIde(isPopularIde) }
    LOG.debug { "Total number of groups for IDE is: $totalNumberOfGroups. Is popular IDE: $isPopularIde." }
    return totalNumberOfGroups
  }

  private fun isPopularIDE() = PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro()

  private fun getExperimentControlOption(): ABExperimentControlOption {
    return getJbABExperimentOptionList().asSequence().filterIsInstance<ABExperimentControlOption>().first()
  }
}