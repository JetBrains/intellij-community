// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab

import com.intellij.platform.experiment.ab.impl.ABExperimentDecision
import com.intellij.platform.experiment.ab.impl.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.ExperimentAssignment
import com.intellij.platform.experiment.ab.impl.IntelliJPlatformProduct
import com.intellij.platform.experiment.ab.impl.NUMBER_OF_BUCKETS
import com.intellij.platform.experiment.ab.impl.experimentsPartition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ABExperimentSanityTest {

  @Test
  fun `no experiments skip the user decision lookup`() {
    assertFalse(ABExperimentOption.NEW_USERS_ONBOARDING.isEnabled(emptyList()) {
      error("The user decision must not be retrieved when no experiments are configured")
    })
  }

  @Test
  fun `an inactive experiment skips the user decision lookup`() {
    val partition = listOf(assignment(ABExperimentOption.CLION_WIZARD_REMOVAL))

    assertFalse(ABExperimentOption.NEW_USERS_ONBOARDING.isEnabled(partition) {
      error("The user decision must not be retrieved for an inactive experiment")
    })
  }

  @Test
  fun `an active experiment retrieves the user decision`() {
    val partition = listOf(assignment(ABExperimentOption.NEW_USERS_ONBOARDING))
    var decisionLookups = 0

    val enabled = ABExperimentOption.NEW_USERS_ONBOARDING.isEnabled(partition) {
      decisionLookups++
      ABExperimentDecision(ABExperimentOption.UNASSIGNED, isControlGroup = true, bucketNumber = 2)
    }

    assertFalse(enabled)
    assertEquals(1, decisionLookups)
  }

  @Test
  fun `the unassigned option fails before the user decision lookup`() {
    assertThrows(IllegalArgumentException::class.java) {
      ABExperimentOption.UNASSIGNED.isEnabled(emptyList()) {
        error("The user decision must not be retrieved for the unassigned option")
      }
    }
  }


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

  private fun assignment(option: ABExperimentOption): ExperimentAssignment = ExperimentAssignment(
    experiment = option,
    experimentBuckets = setOf(0),
    controlBuckets = setOf(1),
  )
}
