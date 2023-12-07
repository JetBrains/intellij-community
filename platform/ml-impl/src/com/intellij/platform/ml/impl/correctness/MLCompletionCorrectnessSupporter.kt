// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness

import com.intellij.lang.Language
import com.intellij.platform.ml.impl.correctness.autoimport.ImportFixer
import com.intellij.platform.ml.impl.correctness.checker.CorrectnessChecker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface MLCompletionCorrectnessSupporter {
  val correctnessChecker: CorrectnessChecker
  val importFixer: ImportFixer

  companion object {
    fun getInstance(language: Language): MLCompletionCorrectnessSupporter? {
      return MLCompletionCorrectnessSupporterEP.EP_NAME.lazySequence().map { it.instance }.firstOrNull { it.isLanguageSupported(language) }
    }
  }

  fun isLanguageSupported(language: Language): Boolean
}