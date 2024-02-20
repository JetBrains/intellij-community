// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab

abstract class ABExperimentConfigBase {
  open val experimentalGroupNumbersForPopularIde = (0..7).toList()
  open val controlGroupNumbersForPopularIde = listOf(8, 9)
  open val experimentalGroupNumbersForRegularIde = listOf(0, 1)
  open val controlGroupNumbersForRegularIde = listOf(2)
  open val totalNumberOfBuckets = 256
}
