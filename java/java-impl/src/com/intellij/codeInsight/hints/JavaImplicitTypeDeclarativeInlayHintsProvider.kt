// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*

public class JavaImplicitTypeDeclarativeInlayHintsProvider : InlayHintsProvider {
  public companion object {
    public const val PROVIDER_ID : String = "java.implicit.types"
  }

  override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    return Collector()
  }

  private class Collector : SharedBypassCollector {
    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
      if (element is PsiLocalVariable) {
        val initializer = element.initializer ?: return
        val identifier = element.identifyingElement ?: return
        if (initializer is PsiLiteral ||
            initializer is PsiPolyadicExpression ||
            initializer is PsiNewExpression ||
            initializer is PsiTypeCastExpression) {
          return
        }
        if (!element.typeElement.isInferredType) return
        val type = element.type
        if (type == PsiTypes.nullType()) return
        sink.addPresentation(InlineInlayPosition(identifier.textRange.endOffset, true), hasBackground = true) {
          text(": ")
          JavaTypeHintsFactory.typeHint(type, this)
        }
      }
    }
  }
}