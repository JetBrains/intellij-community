// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.psi.*

/**
 * Creates InlayPresentation for given PsiType.
 * @param myFoldingLevel level at which generics will be shown as placeholders, so they require click to expand it.
 */
class JavaTypeHintsPresentationFactory(private val myFactory: PresentationFactory, private val myFoldingLevel: Int) {
  fun typeHint(type: PsiType): InlayPresentation = myFactory.roundWithBackground(hint(type, 0))

  private fun hint(type: PsiType, level: Int): InlayPresentation = when (type) {
    is PsiArrayType -> myFactory.seq(hint(type.componentType, level), myFactory.smallText("[]"))
    is PsiClassType -> classTypeHint(type, level)
    is PsiCapturedWildcardType -> myFactory.seq(myFactory.smallText("capture of "), hint(type.wildcard, level))
    is PsiWildcardType -> wildcardHint(type, level)
    is PsiEllipsisType -> myFactory.seq(hint(type.componentType, level), myFactory.smallText("..."))
    is PsiDisjunctionType -> join(type.disjunctions.map { hint(it, level) }, " | ")
    is PsiIntersectionType -> join(type.conjuncts.map { hint(it, level) }, " & ")
    else -> myFactory.smallText(type.presentableText)
  }

  private fun classTypeHint(classType: PsiClassType, level: Int): InlayPresentation {
    val qualifierPresentation = when (val aClass = classType.resolve()) {
      null -> null
      else -> when (val qualifier = aClass.containingClass) {
        null -> null
        else -> classHint(qualifier, level)
      }
    }

    val className = myFactory.psiSingleReference(myFactory.smallText(classType.className ?: ANONYMOUS_MARK)) {
      classType.resolve()
    }
    if (classType.parameterCount == 0) {
      if (qualifierPresentation == null) {
        return className
      }
      else {
        return joinWithDot(qualifierPresentation, className)
      }
    }
    val presentations = mutableListOf(joinWithDot(qualifierPresentation, className))
    val collapsible = myFactory.collapsible(
      prefix = myFactory.smallText("<"),
      collapsed = myFactory.smallText(PLACEHOLDER_MARK),
      expanded = { parametersHint(classType, level) },
      suffix = myFactory.smallText(">"),
      startWithPlaceholder = level > myFoldingLevel
    )
    presentations.add(collapsible)
    return SequencePresentation(presentations)
  }

  private fun parametersHint(classType: PsiClassType, level: Int): InlayPresentation {
    return join(classType.parameters.map { hint(it, level + 1) }, ", ")
  }

  private fun classHint(aClass: PsiClass, level: Int): InlayPresentation? {
    if (aClass.name == null) return null
    val containingClass = aClass.containingClass
    val containingClassPresentation = when {
      containingClass != null -> classHint(containingClass, level)
      else -> null
    }

    val className = myFactory.psiSingleReference(getName(aClass)) { aClass }
    if (!aClass.hasTypeParameters()) {
      return if (containingClassPresentation != null) {
        myFactory.seq(containingClassPresentation, myFactory.smallText("."), className)
      }
      else {
        className
      }
    }
    val presentations = mutableListOf(joinWithDot(containingClassPresentation, className))
    presentations.add(with(myFactory) {
      collapsible(
        prefix = smallText("<"),
        collapsed = smallText(PLACEHOLDER_MARK),
        expanded = { join(aClass.typeParameters.map { myFactory.psiSingleReference(getName(it), resolve = { it }) }, ", ") },
        suffix = smallText(">"),
        startWithPlaceholder = false
      )
    })
    return SequencePresentation(presentations)
  }


  private fun wildcardHint(wildcardType: PsiWildcardType, level: Int): InlayPresentation {
    val (type, bound) = when {
      wildcardType.isExtends -> "extends" to wildcardType.extendsBound
      wildcardType.isSuper -> "super" to wildcardType.superBound
      else -> return myFactory.smallText("?")
    }
    return myFactory.seq(myFactory.smallText("? $type "), hint(bound, level))
  }

  private fun joinWithDot(first: InlayPresentation?, second: InlayPresentation): InlayPresentation {
    if (first == null) {
      return second
    }
    return myFactory.seq(first, myFactory.smallText("."), second)
  }

  private fun getName(element: PsiNamedElement): InlayPresentation {
    return myFactory.smallText(element.name ?: ANONYMOUS_MARK)

  }

  private fun join(presentations: Iterable<InlayPresentation>, text: String) : InlayPresentation {
    val seq = mutableListOf<InlayPresentation>()
    var first = true
    for (presentation in presentations) {
      if (!first) {
        seq.add(myFactory.smallText(text))
      }
      seq.add(presentation)
      first = false
    }
    return SequencePresentation(seq)
  }

  companion object {
    @JvmStatic
    fun presentation(type: PsiType, factory: PresentationFactory): InlayPresentation {
      val base = JavaTypeHintsPresentationFactory(factory, 3).typeHint(type)
      return factory.roundWithBackground(base)
    }

    private const val ANONYMOUS_MARK = "anonymous"
    private const val PLACEHOLDER_MARK = "..."
  }
}