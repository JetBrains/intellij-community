// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.resolve

import com.intellij.find.usages.api.PsiUsage
import com.intellij.util.AbstractQuery
import com.intellij.util.Processor

class LoggingArgumentPsiUsageQuery(private val usage: PsiUsage) : AbstractQuery<PsiUsage>() {
  override fun processResults(consumer: Processor<in PsiUsage>): Boolean {
    return consumer.process(usage)
  }
}