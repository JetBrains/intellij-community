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
    val className = factory.navigateSingle(factory.text(classType.className)) { classType.resolve() }
    if (classType.parameterCount == 0) return className
    val presentations = mutableListOf(className)
    presentations.add(factory.text("<"))
    classType.parameters.mapTo(presentations) { visitType(it)!! }
    presentations.add(factory.text(">"))
    return SequencePresentation(presentations)
  }



  override fun visitType(type: PsiType): InlayPresentation? {
    return factory.text(type.presentableText)
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