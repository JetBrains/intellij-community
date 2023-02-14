// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.textMatching

import org.junit.Assert
import org.junit.Test

class SimilarityScorerTest {
  @Test
  fun `test single similarity score`() {
    val scorer = SimilarityScorer(listOf("filesCount"))
    scorer.checkScores("FileCountUtil", listOf(0.666))
    scorer.checkScores("FileCount", listOf(1.0))
    scorer.checkScores("new", listOf(0.0))
  }

  @Test
  fun `test similarity scores`() {
    val scorer = SimilarityScorer(listOf("project", "existingProject"))
    scorer.checkScores("Project", listOf(1.0, 1.0))
    scorer.checkScores("createProject", listOf(0.5, 0.5))
    scorer.checkScores("projectExists", listOf(0.5, 1.0))
  }

  private fun SimilarityScorer.checkScores(elementText: String, expectedScores: List<Double>) {
    val delta = 0.001
    val scores = score(elementText)
    Assert.assertArrayEquals(scores.toDoubleArray(), expectedScores.toDoubleArray(), delta)
  }
}