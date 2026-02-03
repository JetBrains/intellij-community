// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast

import com.intellij.lang.Language
import com.intellij.psi.HintedPsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

public class UastHintedVisitorAdapter(private val plugin: UastLanguagePlugin,
                               private val visitor: AbstractUastNonRecursiveVisitor,
                               private val directOnly: Boolean,
                               private val uElementTypesHint: Array<Class<out UElement>>
) : PsiElementVisitor(), HintedPsiElementVisitor {

  override fun getHintPsiElements(): List<Class<*>> {
    if (uElementTypesHint.isEmpty()) return emptyList()

    return plugin.getPossiblePsiSourceTypes(*uElementTypesHint).toList()
  }

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    val uElement = plugin.convertElementWithParent(element, uElementTypesHint) ?: return
    if (directOnly && uElement.sourcePsi !== element) return
    uElement.accept(visitor)
  }

  public companion object {
    @JvmStatic
    @JvmOverloads
    public fun create(language: Language,
               visitor: AbstractUastNonRecursiveVisitor,
               uElementTypesHint: Array<Class<out UElement>>,
               directOnly: Boolean = true): PsiElementVisitor {
      val plugin = UastLanguagePlugin.byLanguage(language) ?: return EMPTY_VISITOR
      if (uElementTypesHint.size == 1) {
        return SimpleUastHintedVisitorAdapter(plugin, visitor, uElementTypesHint[0], directOnly)
      }

      return UastHintedVisitorAdapter(plugin, visitor, directOnly, uElementTypesHint)
    }
  }
}

private class SimpleUastHintedVisitorAdapter(val plugin: UastLanguagePlugin,
                                             val visitor: AbstractUastNonRecursiveVisitor,
                                             val uElementTypesHint: Class<out UElement>,
                                             val directOnly: Boolean
) : PsiElementVisitor(), HintedPsiElementVisitor {

  override fun visitElement(element: PsiElement) {
    val uElement = plugin.convertElementWithParent(element, uElementTypesHint) ?: return
    if (!directOnly || uElement.sourcePsi === element) {
      uElement.accept(visitor)
    }
  }

  override fun getHintPsiElements(): List<Class<*>> {
    return plugin.getPossiblePsiSourceTypes(uElementTypesHint).toList()
  }
}