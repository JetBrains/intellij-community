// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.jvm.url

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.microservices.url.parameters.RenameableSemElement
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.intellij.semantic.SemService
import com.intellij.util.Plow
import com.intellij.util.ProcessingContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public class RenameableSemElementCompletionContributor : CompletionContributor() {
  init {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.position
        getParameterNameVariants(position)
          ?.processWith { e -> result.addElement(e); !result.isStopped }
      }
    })
  }
}

internal fun getParameterNameVariants(position: PsiElement): Plow<LookupElement>? {
  val semService = SemService.getSemService(position.project)
  return position.parents(true).take(2)
    .mapNotNull { semService.getSemElement(RenameableSemElement.RENAMEABLE_SEM_KEY, it) }
    .firstOrNull()
    ?.nameVariants
}