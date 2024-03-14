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
  val id: String

  /**
   * Returns a size of an audience group for the option.
   *
   * The number of A/B experimental groups is limited.
   * For most cases, a small group size will be sufficient.
   *
   * If group capacity is exhausted for a specific IDE, there will be an error in runtime.
   * In such a case, you need to communicate with related persons of other options
   * to handle such a case and rearrange option groups accordingly.
   *
   * @see com.intellij.platform.experiment.ab.impl.experiment.ABExperiment.TOTAL_NUMBER_OF_GROUPS
   */
  fun getGroupSizeForIde(isPopular: Boolean): ABExperimentOptionGroupSize

  fun getPluginDescriptor(): PluginDescriptor
}