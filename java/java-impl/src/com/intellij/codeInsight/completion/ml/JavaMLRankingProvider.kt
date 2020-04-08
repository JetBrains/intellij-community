// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.internal.ml.*
import com.intellij.internal.ml.completion.CompletionRankingModelBase
import com.intellij.internal.ml.completion.JarCompletionModelProvider
import com.intellij.lang.Language
import com.jetbrains.completion.ranker.model.java.MLGlassBox

class JavaMLRankingProvider : JarCompletionModelProvider("Java", "java_features") {
  override fun createModel(metadata: ModelMetadata): DecisionFunction {
    return object : CompletionRankingModelBase(metadata) {
      override fun predict(features: DoubleArray?): Double = MLGlassBox.makePredict(features)
    }
  }

  override fun isLanguageSupported(language: Language): Boolean = language.id.compareTo("java", ignoreCase = true) == 0
}