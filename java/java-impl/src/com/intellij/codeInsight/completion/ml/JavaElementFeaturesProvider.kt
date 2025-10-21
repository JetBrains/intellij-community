// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.JavaIncorrectElements
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.annotations.VisibleForTesting
import java.util.Locale

@VisibleForTesting
public class JavaElementFeaturesProvider : ElementFeatureProvider {

  private inline val POPULAR_MODIFIERS: List<JvmModifier>
    get() =
      listOf(JvmModifier.PUBLIC, JvmModifier.PRIVATE, JvmModifier.PROTECTED,
             JvmModifier.ABSTRACT, JvmModifier.FINAL, JvmModifier.STATIC)

  override fun getName(): String = "java"

  override fun calculateFeatures(element: LookupElement,
                                 location: CompletionLocation,
                                 contextFeatures: ContextFeatures): Map<String, MLFeatureValue> {
    val features = mutableMapOf<String, MLFeatureValue>()
    val psi = element.psiElement

    if (psi is PsiModifierListOwner) {
      for (modifier in POPULAR_MODIFIERS) {
        if (psi.hasModifier(modifier)) {
          features["is_${modifier.name.lowercase(Locale.getDefault())}"] = MLFeatureValue.binary(true)
        }
      }
    }
    when (psi) {
      is PsiMethod -> {
        features.putAll(JavaCompletionFeatures.getArgumentsVariablesMatchingFeatures(contextFeatures, psi))
        psi.returnType?.let {
          features["return_type"] = MLFeatureValue.categorical(JavaCompletionFeatures.asJavaType(it))
        }
        features.addStartsWithFeature(element, "is")
        features.addStartsWithFeature(element, "get")
        features.addStartsWithFeature(element, "set")
      }
      is PsiClass -> {
        features.putAll(JavaCompletionFeatures.getPackageMatchingFeatures(contextFeatures, psi))
        JavaCompletionFeatures.getChildClassTokensMatchingFeature(contextFeatures, element.lookupString)?.let {
          features["child_class_tokens_matches"] = it
        }
        if (element.lookupString.lowercase(Locale.getDefault()).contains("util")) {
          features["util_in_class_name"] = MLFeatureValue.binary(true)
        }
        if (psi.isInterface) {
          features["is_interface"] = MLFeatureValue.binary(true)
        }
        if (psi.isEnum) {
          features["is_enum"] = MLFeatureValue.binary(true)
        }
      }
      is PsiKeyword -> {
        JavaCompletionFeatures.asKeyword(element.lookupString)?.let {
          features["keyword_name"] = MLFeatureValue.categorical(it)
        }
      }
    }
    val matcher = JavaIncorrectElements.tryGetMatcher(contextFeatures)
    if (matcher != null && matcher(element)) {
      features["incorrect_element"] = MLFeatureValue.binary(true)
    }

    return features
  }

  private fun MutableMap<String, MLFeatureValue>.addStartsWithFeature(element: LookupElement, prefix: String) {
    if (element.lookupString.startsWith(prefix)) {
      this["method_starts_with_$prefix"] = MLFeatureValue.binary(true)
    }
  }
}