// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.ml

import com.intellij.internal.ml.catboost.CatBoostJarCompletionModelProvider
import com.intellij.internal.ml.completion.DecoratingItemsPolicy
import com.intellij.java.JavaBundle
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public class JavaMLRankingProvider : CatBoostJarCompletionModelProvider(JavaBundle.message("settings.completion.ml.java.display.name"),
                                                                        "java_features", "java_model") {

  override fun isLanguageSupported(language: Language): Boolean = JavaLanguage.INSTANCE == language

  override fun isEnabledByDefault(): Boolean {
    return true
  }

  override fun getDecoratingPolicy(): DecoratingItemsPolicy = DecoratingItemsPolicy.Composite(
    DecoratingItemsPolicy.ByAbsoluteThreshold(3.0),
    DecoratingItemsPolicy.ByRelativeThreshold(2.0)
  )
}