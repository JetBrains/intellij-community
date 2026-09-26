// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.turboComplete

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class KindExecutingCompletionContributor : CompletionContributor(), KindCollector {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    SmartPipelineRunner.getOneOrDefault().runPipeline(this, parameters, result)
  }
}