// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class UastHintedVisitorAdapter(private val plugin: UastLanguagePlugin,
                                    private val visitor: AbstractUastNonRecursiveVisitor,
                                    private val directOnly: Boolean,
                                    private val uElementTypesHint: Array<Class<out UElement>>
) : PsiElementVisitor() {

  override fun visitElement(element: PsiElement) {
    super.visitElement(element)
    val uElement = plugin.convertElementWithParent(element, uElementTypesHint) ?: return
    if (directOnly && uElement.sourcePsi !== element) return
    uElement.accept(visitor)
  }

  companion object {
    @JvmStatic
    @JvmOverloads
    fun create(language: Language,
               visitor: AbstractUastNonRecursiveVisitor,
               uElementTypesHint: Array<Class<out UElement>>,
               directOnly: Boolean = true): PsiElementVisitor {
      val plugin = UastLanguagePlugin.byLanguage(language) ?: return EMPTY_VISITOR
      if (uElementTypesHint.size == 1) {
        return object: PsiElementVisitor() {
          override fun visitElement(element: PsiElement) {
            val uElement = plugin.convertElementWithParent(element, uElementTypesHint[0]) ?: return
            if (!directOnly || uElement.sourcePsi === element) {
              uElement.accept(visitor)
            }
          }
        }
      }
      return UastHintedVisitorAdapter(plugin, visitor, directOnly, uElementTypesHint)
    }
  }
}