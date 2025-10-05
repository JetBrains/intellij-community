// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.psi.*

/**
 * Creates InlayPresentation for given PsiType.
 * @param myFoldingLevel level at which generics will be shown as placeholders, so they require click to expand it.
 * @param maxLength length of the text after which everything will be folded
 */
public class JavaTypeHintsPresentationFactory(private val myFactory: PresentationFactory, private val myFoldingLevel: Int, private val maxLength: Int = DEFAULT_LENGTH) {
  public fun typeHint(type: PsiType): InlayPresentation = myFactory.roundWithBackground(hint(type, 0, Context(maxLength)))

  private val captureOfLabel = "capture of "

  private fun hint(type: PsiType, level: Int, context: Context): InlayPresentation = when (type) {
    is PsiArrayType -> {
      context.lengthAvailable -= 2
      myFactory.seq(hint(type.componentType, level, context), myFactory.smallText("[]"))
    }
    is PsiClassType -> classTypeHint(type, level, context)
    is PsiCapturedWildcardType -> myFactory.seq(myFactory.smallText(captureOfLabel), hint(type.wildcard, level, context))
    is PsiWildcardType -> wildcardHint(type, level, context)
    is PsiDisjunctionType -> {
      context.lengthAvailable -= 3
      join(type.disjunctions.map { hint(it, level, context) }, " | ", context)
    }
    is PsiIntersectionType -> {
      context.lengthAvailable -= 3
      join(type.conjuncts.map { hint(it, level, context) }, " & ", context)
    }
    else -> {
      myFactory.smallText(type.presentableText)
    }
  }

  private fun classTypeHint(classType: PsiClassType, level: Int, context: Context): InlayPresentation {
    val qualifierPresentation = when (val aClass = classType.resolve()) {
      null -> null
      else -> {
        val qualifier = aClass.containingClass
        if (qualifier == null || aClass.hasModifierProperty(PsiModifier.STATIC)) null
        else classHint(qualifier, level, context)
      }
    }

    val className = myFactory.psiSingleReference(myFactory.smallText(classType.className ?: ANONYMOUS_MARK), withDebugToString = true) {
      classType.resolve()
    }
    if (classType.parameterCount == 0) {
      if (qualifierPresentation == null) {
        return className
      }
      else {
        return joinWithDot(qualifierPresentation, className, context)
      }
    }
    val presentations = mutableListOf(joinWithDot(qualifierPresentation, className, context))
    val collapsible = myFactory.collapsible(
      prefix = myFactory.smallText("<"),
      collapsed = myFactory.smallText(PLACEHOLDER_MARK),
      expanded = { parametersHint(classType, level, context) },
      suffix = myFactory.smallText(">"),
      startWithPlaceholder = level > myFoldingLevel || context.lengthAvailable <= 5
    )
    presentations.add(collapsible)
    return SequencePresentation(presentations)
  }

  private fun parametersHint(classType: PsiClassType, level: Int, context: Context): InlayPresentation {
    context.lengthAvailable -= 2
    return join(classType.parameters.map { hint(it, level + 1, context) }, ", ", context)
  }

  private fun classHint(aClass: PsiClass, level: Int, context: Context): InlayPresentation? {
    if (aClass.name == null) return null
    val containingClass = aClass.containingClass
    val containingClassPresentation = when {
      containingClass != null -> classHint(containingClass, level, context)
      else -> null
    }

    val className = reference(aClass, context)
    if (!aClass.hasTypeParameters()) {
      return if (containingClassPresentation != null) {
        context.lengthAvailable -= 1
        myFactory.seq(containingClassPresentation, myFactory.smallText("."), className)
      }
      else {
        className
      }
    }
    val presentations = mutableListOf(joinWithDot(containingClassPresentation, className, context))
    presentations.add(with(myFactory) {
      context.lengthAvailable -= 2
      collapsible(
        prefix = smallText("<"),
        collapsed = smallText(PLACEHOLDER_MARK),
        expanded = { join(aClass.typeParameters.map { reference(it, context) }, ", ", context) },
        suffix = smallText(">"),
        startWithPlaceholder = level > myFoldingLevel || context.lengthAvailable <= 5
      )
    })
    return SequencePresentation(presentations)
  }

  private fun reference(named: PsiNamedElement, context: Context): InlayPresentation {
    val pointer = SmartPointerManager.createPointer(named)
    return myFactory.psiSingleReference(getName(named, context), withDebugToString = true, resolve = { pointer.element })
  }


  private fun wildcardHint(wildcardType: PsiWildcardType, level: Int, context: Context): InlayPresentation {
    val (type, bound) = when {
      wildcardType.isExtends -> "extends" to wildcardType.extendsBound
      wildcardType.isSuper -> "super" to wildcardType.superBound
      else -> return myFactory.smallText("?")
    }
    return myFactory.seq(myFactory.smallText("? $type "), hint(bound, level, context))
  }

  private fun joinWithDot(first: InlayPresentation?, second: InlayPresentation, context: Context): InlayPresentation {
    if (first == null) {
      return second
    }
    context.lengthAvailable -= 1
    return myFactory.seq(first, myFactory.smallText("."), second)
  }

  private fun getName(element: PsiNamedElement, context: Context): InlayPresentation {
    val text = element.name ?: ANONYMOUS_MARK
    context.lengthAvailable -= text.length
    return myFactory.smallText(text)
  }

  private fun join(presentations: Iterable<InlayPresentation>, separator: String, context: Context) : InlayPresentation {
    val seq = mutableListOf<InlayPresentation>()
    var first = true
    for (presentation in presentations) {
      if (!first) {
        context.lengthAvailable -= separator.length
        seq.add(myFactory.smallText(separator))
      }
      seq.add(presentation)
      first = false
    }
    return SequencePresentation(seq)
  }

  public companion object {
    @JvmStatic
    public fun presentation(type: PsiType, factory: PresentationFactory): InlayPresentation {
      val base = JavaTypeHintsPresentationFactory(factory, 3).typeHint(type)
      return factory.roundWithBackground(base)
    }

    @JvmStatic
    public fun presentationWithColon(type: PsiType, factory: PresentationFactory): InlayPresentation {
      val presentations = JavaTypeHintsPresentationFactory(factory, 3).hint(type, 0, Context(DEFAULT_LENGTH))
      return factory.roundWithBackground(factory.seq(factory.smallText(": "), presentations))
    }

    private const val ANONYMOUS_MARK = "anonymous"
    private const val PLACEHOLDER_MARK = "..."
    private const val DEFAULT_LENGTH = 15
  }

  // Quite hacky
  private class Context(var lengthAvailable: Int)
}