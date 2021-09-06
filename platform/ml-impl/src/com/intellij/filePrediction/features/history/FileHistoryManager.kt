// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction.features.history

import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.openapi.project.Project

data class FileHistoryFeatures(val position: Int?, val uniGram: NextFileProbability, val biGram: NextFileProbability)

data class NextFileProbability(
  val mle: Double, val minMle: Double, val maxMle: Double, val mleToMin: Double, val mleToMax: Double
)

class FileHistoryManager(private val model: NGramIncrementalModelRunner) {

  fun saveFileHistory(project: Project) {
    FileHistoryPersistence.saveNGrams(project, model)
  }

  fun onFileOpened(fileUrl: String) {
    model.learnNextToken(fileUrl)
  }

  fun calcNGramFeatures(candidates: List<String>): FilePredictionNGramFeatures {
    val result: MutableMap<String, Double> = hashMapOf()
    val scorer = model.createScorer()
    for (candidate in candidates) {
      result[candidate] = scorer.score(candidate)
    }
    return FilePredictionNGramFeatures(result)
  }

  fun calcNextFileProbability(fileUrl: String): Double {
    val scorer = model.createScorer()
    return scorer.score(fileUrl)
  }
}