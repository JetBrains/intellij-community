// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.experiment

import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.experiment.ab.impl.option.ABExperimentOptionGroupSize

/**
 * A/B Experiment option interface.
 *
 * Implement and register an option for your feature.
 *
 * @see com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionBase
 */
interface ABExperimentOption : PluginAware {
  val id: ABExperimentOptionId

  /**
   * Returns a size of an audience group for the option.
   *
   * The number of A/B experimental groups is limited.
   * The group size must be agreed with the analysts so that the result of the experiment is statistically significant.
   *
   * If group capacity is exhausted for a specific IDE, there will be an error in runtime.
   * In such a case, you need to communicate with related persons of other options
   * to handle such a case and rearrange option groups accordingly.
   *
   * @see com.intellij.platform.experiment.ab.impl.experiment.ABExperiment.TOTAL_NUMBER_OF_GROUPS
   */
  fun getGroupSizeForIde(isPopular: Boolean): ABExperimentOptionGroupSize

  /**
   * Check if the option should be enabled in a certain IDE.
   *
   * Mostly useful for options in Intellij Platform to enable option only in certain IDEs.
   */
  fun checkIdeIsSuitable(): Boolean

  /**
   * Check if the option should be enabled in a certain IDE version.
   *
   * Must be agreed with analytics to do not spoil an experiment.
   *
   * Mostly useful for plugins that potentially can be installed to a different version of a certain IDE.
   * In this case, the plugin must specify the target version so as not to spoil the experiment
   * by overflowing the maximum number of user groups.
   *
   * For IDEs it allows to control in what version of IDE what options are enabled.
   */
  fun checkIdeVersionIsSuitable(): Boolean

  fun getPluginDescriptor(): PluginDescriptor
}

fun ABExperimentOption.isEnabled(): Boolean {
  return checkIdeIsSuitable() && checkIdeVersionIsSuitable()
}