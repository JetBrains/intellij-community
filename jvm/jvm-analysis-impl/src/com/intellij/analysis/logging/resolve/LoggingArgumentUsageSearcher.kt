// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.logging.resolve

import com.intellij.find.usages.api.PsiUsage
import com.intellij.find.usages.api.Usage
import com.intellij.find.usages.api.UsageSearchParameters
import com.intellij.find.usages.api.UsageSearcher
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.util.Query
import com.siyeh.ig.format.DefUsage

class LoggingArgumentUsageSearcher : UsageSearcher {
  override fun collectSearchRequests(parameters: UsageSearchParameters): Collection<Query<out Usage>> {
    val target = parameters.target
    if (target !is LoggingArgumentSymbol) return emptyList()
    val uLiteralExpression = target.getPlaceholderString() ?: return emptyList()
    return getLogArgumentReferences(uLiteralExpression)?.let {
      it.filter { ref: PsiSymbolReference -> ref.resolvesTo(target) }
        .flatMap { usage -> listOf(PsiUsage.textUsage(usage), DefUsage(target.expression)) }
        .map { psiUsage -> LoggingArgumentPsiUsageQuery(psiUsage) }
    } ?: emptyList()
  }
}