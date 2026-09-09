// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.template

import com.intellij.lang.jvm.JvmClass
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiVariable
import org.jetbrains.annotations.ApiStatus

/** Supplies code insight rules for Java embedded in a template. */
@ApiStatus.Internal
public interface JavaTemplateCodeInsightSupport {
  public fun supportsJavaCompletion(file: PsiFile): Boolean = false
  public fun requiresFinalVariable(place: PsiElement): Boolean = true
  public fun declarationContext(element: PsiElement): PsiElement? = element
  public fun escapeInsertionText(file: PsiFile, text: String): String = text
  public fun actionClass(target: JvmClass): PsiClass? = null
  public fun allowsIndexingLexer(file: PsiFile): Boolean = true
  public fun allowsCommentIndexing(file: PsiFile): Boolean = true
  public fun allowsEmptyStatementInspection(file: PsiFile): Boolean = true
  public fun allowsVariableMovement(insertionPoint: PsiElement, variable: PsiVariable): Boolean = true
  public fun supportsStatementContainer(element: PsiElement): Boolean = false
  public fun referenceResolutionFile(file: PsiFile): PsiFile? = file

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<JavaTemplateCodeInsightSupport> =
      ExtensionPointName.create("com.intellij.java.templateCodeInsightSupport")

    @JvmStatic
    public fun isJavaCompletionSupported(file: PsiFile): Boolean = EP_NAME.extensionList.any { it.supportsJavaCompletion(file) }

    @JvmStatic
    public fun isFinalVariableRequired(place: PsiElement): Boolean = EP_NAME.extensionList.all { it.requiresFinalVariable(place) }

    @JvmStatic
    public fun getDeclarationContext(element: PsiElement?): PsiElement? =
      EP_NAME.extensionList.fold(element) { context, support ->
        context?.let { support.declarationContext(it) }
      }

    @JvmStatic
    public fun escapeText(file: PsiFile, text: String): String =
      EP_NAME.extensionList.fold(text) { result, support -> support.escapeInsertionText(file, result) }

    @JvmStatic
    public fun getActionClass(target: JvmClass): PsiClass? = EP_NAME.extensionList.firstNotNullOfOrNull { it.actionClass(target) }

    @JvmStatic
    public fun isIndexingLexerAllowed(file: PsiFile): Boolean = EP_NAME.extensionList.all { it.allowsIndexingLexer(file) }

    @JvmStatic
    public fun isCommentIndexingAllowed(file: PsiFile): Boolean = EP_NAME.extensionList.all { it.allowsCommentIndexing(file) }

    @JvmStatic
    public fun isEmptyStatementInspectionAllowed(file: PsiFile): Boolean =
      EP_NAME.extensionList.all { it.allowsEmptyStatementInspection(file) }

    @JvmStatic
    public fun isVariableMovementAllowed(insertionPoint: PsiElement, variable: PsiVariable): Boolean =
      EP_NAME.extensionList.all { it.allowsVariableMovement(insertionPoint, variable) }

    @JvmStatic
    public fun isStatementContainerSupported(element: PsiElement): Boolean =
      EP_NAME.extensionList.any { it.supportsStatementContainer(element) }

    @JvmStatic
    public fun getReferenceResolutionFile(file: PsiFile): PsiFile? =
      EP_NAME.extensionList.fold<JavaTemplateCodeInsightSupport, PsiFile?>(file) { result, support ->
        result?.let { support.referenceResolutionFile(it) }
      }
  }
}
