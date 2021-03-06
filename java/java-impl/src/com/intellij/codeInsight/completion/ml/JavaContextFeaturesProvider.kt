// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull

class JavaContextFeaturesProvider : ContextFeatureProvider {
  override fun getName(): String = "java"

  override fun calculateFeatures(environment: @NotNull CompletionEnvironment): @NotNull MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()

    JavaCompletionFeatures.calculateVariables(environment)
    JavaCompletionUtil.getExpectedTypes(environment.parameters)?.forEach {
      features["${JavaCompletionFeatures.asJavaType(it).name.toLowerCase()}_expected"] = MLFeatureValue.binary(true)
    }
    if (JavaCompletionFeatures.isInQualifierExpression(environment)) {
      features["is_in_qualifier_expression"] = MLFeatureValue.binary(true)
    }
    if (JavaCompletionFeatures.isAfterMethodCall(environment)) {
      features["is_after_method_call"] = MLFeatureValue.binary(true)
    }
    PsiTreeUtil.prevVisibleLeaf(environment.parameters.position)?.let { prevLeaf ->
      JavaCompletionFeatures.asKeyword(prevLeaf.text)?.let { keyword ->
        features["prev_neighbour_keyword"] = MLFeatureValue.categorical(keyword)

        if (keyword == JavaCompletionFeatures.JavaKeyword.IMPLEMENTS ||
            keyword == JavaCompletionFeatures.JavaKeyword.EXTENDS) {
          PsiTreeUtil.prevVisibleLeaf(prevLeaf)?.let { childClass ->
            JavaCompletionFeatures.calculateChildClassWords(environment, childClass)
          }
        }
      }
    }
    return features
  }
}