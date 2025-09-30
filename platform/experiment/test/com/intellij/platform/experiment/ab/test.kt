// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.experiment.ab

import com.intellij.platform.experiment.ab.impl.NUMBER_OF_BUCKETS
import com.intellij.platform.experiment.ab.impl.experimentsPartition
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ABExperimentSanityTest {

  @Test
  fun `no intersections in experiment partitions`() {
    for (experiment1 in experimentsPartition) {
      for (experiment2 in experimentsPartition) {
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

  @Test
  fun `all ranges are within bounds`() {
    for (experiment in experimentsPartition) {
      assertWithinBounds(experiment.controlBuckets)
      assertWithinBounds(experiment.experimentBuckets)
    }
  }

  fun assertEmptyIntersection(range1: Set<*>, range2: Set<*>) {
    Assertions.assertTrue(range1.intersect(range2).isEmpty())
  }

  fun assertWithinBounds(range: Set<Int>) = Assertions.assertTrue(range.subtract(0 until NUMBER_OF_BUCKETS).isEmpty())
}