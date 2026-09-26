// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.jsp

import com.intellij.java.impl.template.JavaTemplateCodeInsightSupport
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmClass
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JspPsiUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiVariable
import com.intellij.psi.ServerPageFile
import com.intellij.psi.impl.source.jsp.jspJava.JspClass
import com.intellij.psi.impl.source.jsp.jspJava.JspClassLevelDeclarationStatement
import com.intellij.psi.jsp.JspFile
import com.intellij.psi.jsp.JspxLanguage
import com.intellij.psi.util.FileTypeUtils
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xml.util.JspFileTypeUtil

internal class JspCodeInsightSupport : JavaTemplateCodeInsightSupport {
  override fun supportsJavaCompletion(file: PsiFile): Boolean = JspFileTypeUtil.isJsp(file)
  override fun requiresFinalVariable(place: PsiElement): Boolean = !JspPsiUtil.isInJspFile(place)

  override fun declarationContext(element: PsiElement): PsiElement? =
    if (element is JspClassLevelDeclarationStatement) element.context else element

  override fun escapeInsertionText(file: PsiFile, text: String): String =
    if (file.viewProvider.baseLanguage is JspxLanguage) StringUtil.escapeXmlEntities(text) else text

  override fun actionClass(target: JvmClass): PsiClass? =
    if (target is JspClass && target.language == JavaLanguage.INSTANCE) target else null

  override fun allowsIndexingLexer(file: PsiFile): Boolean = file !is JspFile
  override fun allowsCommentIndexing(file: PsiFile): Boolean = file !is ServerPageFile
  override fun allowsEmptyStatementInspection(file: PsiFile): Boolean = !FileTypeUtils.isInServerPageFile(file)

  override fun allowsVariableMovement(insertionPoint: PsiElement, variable: PsiVariable): Boolean {
    if (!FileTypeUtils.isInServerPageFile(insertionPoint)) return true
    val elementBefore = PsiTreeUtil.skipWhitespacesBackward(insertionPoint.prevSibling)
    return elementBefore !is PsiDeclarationStatement || elementBefore != variable.parent
  }

  override fun supportsStatementContainer(element: PsiElement): Boolean =
    FileTypeUtils.isInServerPageFile(element) && element is PsiFile

  override fun referenceResolutionFile(file: PsiFile): PsiFile? =
    if (file is ServerPageFile) file.viewProvider.getPsi(JavaLanguage.INSTANCE) else file
}
