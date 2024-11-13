// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.internal.statistic.eventLog.fus.MachineIdManager
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.experiment.ab.impl.option.ABExperimentControlOption
import com.intellij.platform.experiment.ab.impl.statistic.ABExperimentCountCollector
import com.intellij.util.MathUtil
import com.intellij.util.PlatformUtils

fun getABExperimentInstance(): ABExperiment {
  return ABExperiment.getABExperimentInstance()
}

/**
 * This is a multi-optional A/B experiment for all IDEs and JetBrains plugins,
 * which affects IDE metrics like user retention in IDE.
 *
 * Each feature is represented as an option.
 * An option defines the number of user groups which will be associated with this option.
 * There is a control option for default behavior.
 * You need to implement `ABExperimentOption` extension point to implement an option for your feature.
 *
 * The number of A/B experimental groups is limited.
 * It is necessary to keep a group audience sufficient to make statistically significant conclusions.
 * So it is crucial to choose group size judiciously.
 * If group capacity is exhausted for a specific IDE, there will be an error.
 * In such a case, you need to communicate with related people to handle such a case and rearrange option groups accordingly.
 *
 * A/B experiment supports the implemented options from JetBrains plugins.
 * Plugins can be installed/uninstalled or enabled/disabled.
 * Accordingly, the options defined in plugins may appear when the plugin is enabled or installed,
 * or disappear when the plugin is disabled or uninstalled.
 * The experiment uses special storage to be able to work with such conditions correctly.
 *
 * @see com.intellij.platform.experiment.ab.impl.option.ABExperimentControlOption
 * @see com.intellij.platform.experiment.ab.impl.experiment.ABExperimentGroupStorageService
 */
interface ABExperiment {
  companion object {
    fun getABExperimentInstance(): ABExperiment {
      val application = ApplicationManager.getApplication() ?: return DummyABExperiment
      return application.service<ABExperimentImpl>()
    }
  }
  fun isControlExperimentOptionEnabled() : Boolean


  fun isExperimentOptionEnabled(experimentOptionClass: Class<out ABExperimentOption>): Boolean
}

internal object DummyABExperiment : ABExperiment {
  // is never a control group
  override fun isControlExperimentOptionEnabled(): Boolean = false

  // always turned on
  override fun isExperimentOptionEnabled(experimentOptionClass: Class<out ABExperimentOption>): Boolean = true
}

@Service
internal class ABExperimentImpl : ABExperiment {
  companion object {
    private val AB_EXPERIMENTAL_OPTION_EP = ExtensionPointName<ABExperimentOptionBean>("com.intellij.experiment.abExperimentOption")
    private val LOG = logger<ABExperiment>()
    private val DEVICE_ID_PURPOSE = "A/B Experiment" + ApplicationInfo.getInstance().shortVersion
    private const val TOTAL_NUMBER_OF_BUCKETS = 1024
    internal const val TOTAL_NUMBER_OF_GROUPS = 256

    internal val OPTION_ID_FREE_GROUP = ABExperimentOptionId("free.option")

    internal fun getJbABExperimentOptionList(): List<ABExperimentOption> {
      return getJbABExperimentOptionBeanList().map { it.instance }
    }

    internal fun getJbABExperimentOptionBeanList(): List<ABExperimentOptionBean> {
      return AB_EXPERIMENTAL_OPTION_EP.extensionList.filter {
        val pluginDescriptor = it.pluginDescriptor
        val pluginInfo = getPluginInfoByDescriptor(pluginDescriptor)
        pluginInfo.isDevelopedByJetBrains() && it.instance.isEnabled()
      }
    }

    internal fun isPopularIDE() = PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro()
  }

  override fun isControlExperimentOptionEnabled(): Boolean {
    return isExperimentOptionEnabled(ABExperimentControlOption::class.java)
  }

  override fun isExperimentOptionEnabled(experimentOptionClass: Class<out ABExperimentOption>): Boolean {
    return experimentOptionClass.isInstance(getUserExperimentOption())
  }

  internal fun getUserExperimentOption(): ABExperimentOption? {
    val userOptionId = getUserExperimentOptionId()
    ABExperimentCountCollector.logABExperimentOptionUsed(userOptionId, getUserGroupNumber(), getUserBucketNumber())
    return getJbABExperimentOptionList().find { it.id.value == userOptionId?.value }
  }

  internal fun getUserExperimentOptionId(): ABExperimentOptionId? {
    val manualOptionIdText = System.getProperty("platform.experiment.ab.manual.option", "")
    if (manualOptionIdText.isNotBlank()) {
      LOG.debug { "Use manual option id from Registry. Registry key value is: $manualOptionIdText" }

      val manualOption = getJbABExperimentOptionList().find { it.id.value == manualOptionIdText }
      if (manualOption != null) {
        LOG.debug { "Found manual option is: $manualOption" }
        return manualOption.id
      }
      else if (manualOptionIdText == OPTION_ID_FREE_GROUP.value) {
        LOG.debug { "Found manual option is: $manualOptionIdText" }
        return ABExperimentOptionId(manualOptionIdText)
      }
      else {
        LOG.debug { "Manual option with id $manualOptionIdText not found." }
        return null
      }
    }

    val userGroupNumber = getUserGroupNumber()
    val userOptionId = ABExperimentGroupStorageService.getUserExperimentOptionId(userGroupNumber)

    LOG.debug { "User option id is: ${userOptionId.value}." }

    return userOptionId
  }

  private fun getUserGroupNumber(): Int {
    val bucket = getUserBucketNumber()
    if (TOTAL_NUMBER_OF_BUCKETS < TOTAL_NUMBER_OF_GROUPS) {
      LOG.error("Number of buckets is less than number of groups. " +
                "Please revise related experiment constants and adjust them accordingly.")
    }

    val experimentGroup = bucket % TOTAL_NUMBER_OF_GROUPS
    LOG.debug { "User group number is: $experimentGroup." }
    return experimentGroup
  }

  private fun getUserBucketNumber(): Int {
    val deviceId = LOG.runAndLogException {
      MachineIdManager.getAnonymizedMachineId(DEVICE_ID_PURPOSE)
    }

    val bucketNumber = MathUtil.nonNegativeAbs(deviceId.hashCode()) % TOTAL_NUMBER_OF_BUCKETS
    LOG.debug { "User bucket number is: $bucketNumber." }
    return bucketNumber
  }
}
