// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.psi.*

/**
 * Creates InlayPresentation for given PsiType.
 * @param foldingLevel level at which generics will be shown as placeholders, so they require click to expand it.
 */
class JavaTypeHintsPresentationFactory(private val factory: PresentationFactory, private val foldingLevel: Int) {
  fun typeHint(type: PsiType): InlayPresentation = hint(type, 0)

  private fun hint(type: PsiType, level: Int): InlayPresentation = when (type) {
    is PsiArrayType -> factory.seq(hint(type.componentType, level), factory.text("[]"))
    is PsiClassType -> classTypeHint(type, level)
    is PsiCapturedWildcardType -> factory.seq(factory.text("capture of "), hint(type.wildcard, level))
    is PsiWildcardType -> wildcardHint(type, level)
    is PsiEllipsisType -> factory.seq(hint(type.componentType, level), factory.text("..."))
    is PsiDisjunctionType -> join(type.disjunctions.map { hint(it, level) }, " | ")
    is PsiIntersectionType -> join(type.conjuncts.map { hint(it, level) }, " & ")
    else -> factory.text(type.presentableText)

  }

  private fun classTypeHint(classType: PsiClassType, level: Int): InlayPresentation {
    // TODO not print qualifier, if it is anonymous class
    //  same for class
    val qualifierPresentation = when (val aClass = classType.resolve()) {
      null -> null
      else -> when (val qualifier = aClass.containingClass) {
        null -> null
        else -> classHint(qualifier, level)
      }
    }
    val className = factory.navigateSingle(factory.text(classType.className)) {
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
    if (level > 0) {
      presentations.add(factory.seq(factory.text("<"), factory.folding(factory.text("...")) { parametersHint(classType, level) }, factory.text(">")))
    } else {
      presentations.add(factory.text("<"))
      presentations.add(parametersHint(classType, level))
      presentations.add(factory.text(">"))
    }
    return SequencePresentation(presentations)
  }

  private fun parametersHint(classType: PsiClassType, level: Int): InlayPresentation {
    return join(classType.parameters.map { hint(it, level + 1) }, ", ")
  }

  private fun classHint(aClass: PsiClass, level: Int): InlayPresentation {
    val containingClass = aClass.containingClass
    val containingClassPresentation = when {
      containingClass != null -> classHint(containingClass, level)
      else -> null
    }

    val className = factory.navigateSingle(getName(aClass)) { aClass }
    if (!aClass.hasTypeParameters()) {
      return if (containingClassPresentation != null) {
        factory.seq(containingClassPresentation, factory.text("."), className)
      }
      else {
        className
      }
    }
    val presentations = mutableListOf(joinWithDot(containingClassPresentation, className))
    presentations.add(factory.text("<"))
    aClass.typeParameters.mapTo(presentations) {
      factory.navigateSingle(getName(it)) { it }
    }
    presentations.add(factory.text(">"))
    return SequencePresentation(presentations)
  }


  private fun wildcardHint(wildcardType: PsiWildcardType, level: Int): InlayPresentation {
    val (type, bound) = when {
      wildcardType.isExtends -> "extends" to wildcardType.extendsBound
      wildcardType.isSuper -> "super" to wildcardType.superBound
      else -> return factory.text("?")
    }
    return factory.seq(factory.text("? $type "), hint(bound, level))
  }

  private fun joinWithDot(first: InlayPresentation?, second: InlayPresentation): InlayPresentation {
    if (first == null) {
      return second
    }
    return factory.seq(first, factory.text("."), second)
  }

  private fun getName(element: PsiNamedElement): InlayPresentation {
    val name = element.name
    if (name != null) {
      return factory.text(name)
    }
    return factory.asWrongReference(factory.text(NO_NAME_MARKER))
  }

  private fun join(presentations: Iterable<InlayPresentation>, text: String) : InlayPresentation {
    val seq = mutableListOf<InlayPresentation>()
    var first = true
    for (presentation in presentations) {
      if (!first) {
        seq.add(factory.text(text))
      }
      seq.add(presentation)
      first = false
    }
    return SequencePresentation(seq)
  }

  companion object {
    @JvmStatic
    fun presentation(type: PsiType, factory: PresentationFactory): InlayPresentation {
      val base = JavaTypeHintsPresentationFactory(factory, 5).typeHint(type)
      return factory.roundWithBackground(base)
    }

    private const val NO_NAME_MARKER = "<NO NAME>"
  }
}