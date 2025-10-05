// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*

public class JavaLambdaParameterTypeHintsProvider : InlayHintsProvider  {
  public companion object {
    public const val PROVIDER_ID : String = "java.implicit.types.lambda"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    return Collector()
  }

  private class Collector : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element !is PsiParameter) return
      if (element.parent !is PsiParameterList) return
      if (element.parent.parent !is PsiLambdaExpression) return
      if (element.typeElement != null) return
      val identifier = element.nameIdentifier ?: return
      val type = element.type
      if (type == PsiTypes.nullType() || type is PsiLambdaParameterType) return
      sink.addPresentation(InlineInlayPosition(identifier.textRange.startOffset, false), hasBackground = true) {
        JavaTypeHintsFactory.typeHint(type, this)
      }
    }
  }
}