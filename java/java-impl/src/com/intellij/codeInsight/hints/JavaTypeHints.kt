// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.*
import java.awt.Graphics2D

// TODO render annotations
class JavaTypeHintsVisitor(val factory: PresentationFactory) : PsiTypeVisitor<InlayPresentation>() {
  override fun visitPrimitiveType(primitiveType: PsiPrimitiveType) : InlayPresentation {
    return factory.text(primitiveType.presentableText)
  }

  override fun visitArrayType(arrayType: PsiArrayType) : InlayPresentation {
    val componentPresentation = visitType(arrayType.componentType)!!
    return factory.seq(componentPresentation, factory.text("[]"))
  }

  override fun visitClassType(classType: PsiClassType): InlayPresentation {
    val qualifierPresentation = when (val aClass = classType.resolve()) {
      null -> null
      else -> when (val qualifier = aClass.containingClass) {
        null -> null
        else -> classPresentation(qualifier)
      }
    }
    val className = factory.navigateSingle(factory.text(classType.className)) {
      classType.resolve()
    }
    if (classType.parameterCount == 0) {
      if (qualifierPresentation == null) {
        return className
      } else {
        return joinWithDot(qualifierPresentation, className)
      }
    }
    val presentations = mutableListOf(joinWithDot(qualifierPresentation, className))
    presentations.add(factory.text("<"))
    classType.parameters.mapTo(presentations) {
      it.accept(this)!!
    }
    presentations.add(factory.text(">"))
    return SequencePresentation(presentations)
  }

  private fun joinWithDot(first: InlayPresentation?, second: InlayPresentation) : InlayPresentation {
    if (first == null) {
      return second
    }
    return factory.seq(first, factory.text("."), second)
  }

  private fun classPresentation(aClass: PsiClass) : InlayPresentation {
    val containingClass = aClass.containingClass
    val containingClassPresentation = when {
      containingClass != null -> classPresentation(containingClass)
      else -> null
    }

    val className = factory.navigateSingle(factory.text(aClass.name ?: NO_NAME_MARKER)) { aClass }
    if (!aClass.hasTypeParameters()) {
      return if (containingClassPresentation != null) {
        factory.seq(containingClassPresentation, factory.text("."), className)
      } else {
        className
      }
    }
    val presentations = mutableListOf(joinWithDot(containingClassPresentation, className))
    presentations.add(factory.text("<"))
    aClass.typeParameters.mapTo(presentations) {
      factory.navigateSingle(factory.text(it.name ?: NO_NAME_MARKER)) { it }
    }
    presentations.add(factory.text(">"))
    return SequencePresentation(presentations)
  }



  override fun visitType(type: PsiType): InlayPresentation? {
    return factory.text(type.presentableText)
  }

  companion object {
    @JvmStatic
    fun presentation(type: PsiType, factory: PresentationFactory) : InlayPresentation {
      val base = type.accept(JavaTypeHintsVisitor(factory))
      return factory.roundWithBackground(base)
    }

    private const val NO_NAME_MARKER = "<NO NAME>"
  }

  //  override fun visitCapturedWildcardType(capturedWildcardType: PsiCapturedWildcardType): InlayPresentation {
//    return super.visitCapturedWildcardType(capturedWildcardType)
//  }
//
//  override fun visitWildcardType(wildcardType: PsiWildcardType): InlayPresentation {
//    return super.visitWildcardType(wildcardType)
//  }
//
//  override fun visitEllipsisType(ellipsisType: PsiEllipsisType): InlayPresentation {
//    return super.visitEllipsisType(ellipsisType)
//  }
//
//  override fun visitDisjunctionType(disjunctionType: PsiDisjunctionType): InlayPresentation {
//    return super.visitDisjunctionType(disjunctionType)
//  }
//
//  override fun visitIntersectionType(intersectionType: PsiIntersectionType): InlayPresentation {
//    return super.visitIntersectionType(intersectionType)
//  }
//
}

// TODO lazy everything!
class JavaTypePresentation(val type: PsiType) : BasePresentation() {
  override val width: Int
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
  override val height: Int
    get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun updateIfNecessary(newPresentation: InlayPresentation): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun toString(): String {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

}