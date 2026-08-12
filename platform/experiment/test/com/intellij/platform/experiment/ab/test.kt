// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab

import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.ABExperimentUserData
import com.intellij.platform.experiment.ab.impl.IntelliJPlatformProduct
import com.intellij.platform.experiment.ab.impl.NUMBER_OF_BUCKETS
import com.intellij.platform.experiment.ab.impl.experimentsPartition
import com.intellij.platform.experiment.ab.impl.getExperimentDecision
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ABExperimentSanityTest {

  @Test
  fun `no intersections in experiment partitions`() {
    for (experiment1 in experimentsPartition) {
      for (experiment2 in experimentsPartition) {
        for (product in IntelliJPlatformProduct.entries) {
          if (!experiment1.products.contains(product) || !experiment2.products.contains(product)) continue
          if (experiment1.experiment == experiment2.experiment) {
            assertEmptyIntersection(experiment1.controlBuckets, experiment2.experimentBuckets)
          }
          else {
            assertEmptyIntersection(experiment1.experimentBuckets, experiment2.controlBuckets)
            assertEmptyIntersection(experiment1.controlBuckets, experiment2.controlBuckets)
            assertEmptyIntersection(experiment1.experimentBuckets, experiment2.experimentBuckets)
            assertEmptyIntersection(experiment1.controlBuckets, experiment2.experimentBuckets)
          }
        }
      }
    }
  }

  @Test
  fun `all ranges are within bounds`() {
    for (experiment in experimentsPartition) {
      assertWithinBounds(experiment.controlBuckets)
      assertWithinBounds(experiment.experimentBuckets)
    }
  }

  @Test
  fun `no experiment without associated products`() {
    for (experiment in experimentsPartition) {
      Assertions.assertTrue(experiment.products.isNotEmpty())
    }
  }

  //@Test
  //Relies on an experiment that was completed -- needs a mock experiment ot function properly. AI generated?"
  //fun `experiment decision uses product version and bucket`() {
  //  val experimentUser = ABExperimentUserData(IntelliJPlatformProduct.IDEA, "2026.1 EAP", 0)
  //  val controlUser = experimentUser.copy(bucketNumber = 256)
  //  val unsupportedProductUser = experimentUser.copy(product = IntelliJPlatformProduct.CLION)
  //  val unsupportedVersionUser = experimentUser.copy(fullVersion = "2026.2")
  //
  //  Assertions.assertEquals(ABExperimentOption.SPLIT_SEARCH_EVERYWHERE, getExperimentDecision(experimentUser).option)
  //  Assertions.assertFalse(getExperimentDecision(experimentUser).isControlGroup)
  //  Assertions.assertEquals(ABExperimentOption.SPLIT_SEARCH_EVERYWHERE, getExperimentDecision(controlUser).option)
  //  Assertions.assertTrue(getExperimentDecision(controlUser).isControlGroup)
  //  Assertions.assertEquals(ABExperimentOption.UNASSIGNED, getExperimentDecision(unsupportedProductUser).option)
  //  Assertions.assertEquals(ABExperimentOption.UNASSIGNED, getExperimentDecision(unsupportedVersionUser).option)
  //}

  fun assertEmptyIntersection(range1: Set<*>, range2: Set<*>) {
    Assertions.assertTrue(range1.intersect(range2).isEmpty())
  }

  fun assertWithinBounds(range: Set<Int>) = Assertions.assertTrue(range.subtract(0 until NUMBER_OF_BUCKETS).isEmpty())
}
