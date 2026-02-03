// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction.features.history

import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class FileHistoryFeatures(val position: Int?, val uniGram: NextFileProbability, val biGram: NextFileProbability)

@ApiStatus.Internal
data class NextFileProbability(
  val mle: Double, val minMle: Double, val maxMle: Double, val mleToMin: Double, val mleToMax: Double
)

@ApiStatus.Internal
class FileHistoryManager(private val model: NGramIncrementalModelRunner) {
  fun saveFileHistoryAsync(project: Project) {
    FileHistoryPersistence.saveNGramsAsync(project, model)
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