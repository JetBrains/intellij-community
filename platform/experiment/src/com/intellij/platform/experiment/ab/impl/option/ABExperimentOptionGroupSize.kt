// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab.impl.option

/**
 * Represent experiment option user group size.
 *
 * Must be greater than 0 and less than the total number of user groups.
 *
 * The group size must be agreed with the analysts so that the result of the experiment is statistically significant.
 *
 * @see com.intellij.platform.experiment.ab.impl.experiment.TOTAL_NUMBER_OF_GROUPS
 */
@JvmInline
value class ABExperimentOptionGroupSize(val groupCount: Int) {
  init {
    if (groupCount <= 0) {
      error("Size of option group should be greater then 0.")
    }
  }
}