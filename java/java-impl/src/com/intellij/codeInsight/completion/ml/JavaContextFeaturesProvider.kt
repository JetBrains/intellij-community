// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.completion.JavaIncorrectElements
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull
import java.util.Locale

public class JavaContextFeaturesProvider : ContextFeatureProvider {
  override fun getName(): String = "java"

  override fun calculateFeatures(environment: @NotNull CompletionEnvironment): @NotNull MutableMap<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()

    JavaCompletionFeatures.calculateVariables(environment)
    JavaCompletionFeatures.collectPackages(environment)
    JavaCompletionUtil.getExpectedTypes(environment.parameters)?.forEach {
      features["${JavaCompletionFeatures.asJavaType(it).name.lowercase(Locale.getDefault())}_expected"] = MLFeatureValue.binary(true)
    }
    if (JavaCompletionFeatures.isInQualifierExpression(environment)) {
      features["is_in_qualifier_expression"] = MLFeatureValue.binary(true)
    }
    if (JavaCompletionFeatures.isAfterMethodCall(environment)) {
      features["is_after_method_call"] = MLFeatureValue.binary(true)
    }
    val position = environment.parameters.position
    PsiTreeUtil.prevVisibleLeaf(position)?.let { prevLeaf ->
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
    val positionMatcher = JavaIncorrectElements.matchPosition(position)
    if (positionMatcher != null) {
      val incorrectElementMatcher = positionMatcher.createIncorrectElementMatcher(position)
      JavaIncorrectElements.putMatcher(incorrectElementMatcher, environment)

      features["position_matcher"] = MLFeatureValue.className(positionMatcher::class.java)
    }
    return features
  }
}