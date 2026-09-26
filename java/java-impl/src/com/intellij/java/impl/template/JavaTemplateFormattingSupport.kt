// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.template

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.annotations.ApiStatus

/** Supplies whitespace and traversal rules for Java embedded in a template. */
@ApiStatus.Internal
public interface JavaTemplateFormattingSupport {
  public fun isAdditionalWhitespace(type: IElementType): Boolean = false
  public fun hasSignificantWhitespace(language: Language): Boolean = false

  /** Returns null for an ordinary file, or the roots to visit for a template file. */
  public fun traversalRoots(file: PsiFile): List<PsiElement>? = null

  /** Returns null for an ordinary file, or the roots used to adjust template references. */
  public fun referenceRoots(file: PsiFile): List<ASTNode>? = null

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<JavaTemplateFormattingSupport> =
      ExtensionPointName.create("com.intellij.java.templateFormattingSupport")

    @JvmStatic
    public fun isWhitespace(type: IElementType): Boolean =
      TokenSet.WHITE_SPACE.contains(type) || EP_NAME.extensionList.any { it.isAdditionalWhitespace(type) }

    @JvmStatic
    public fun isWhitespaceSignificant(language: Language): Boolean = EP_NAME.extensionList.any { it.hasSignificantWhitespace(language) }

    @JvmStatic
    public fun visitTemplateFile(file: PsiFile, visitor: PsiElementVisitor): Boolean {
      val roots = EP_NAME.extensionList.firstNotNullOfOrNull { it.traversalRoots(file) } ?: return false
      roots.forEach { it.accept(visitor) }
      return true
    }

    @JvmStatic
    public fun getReferenceRoots(file: PsiFile): List<ASTNode>? = EP_NAME.extensionList.firstNotNullOfOrNull { it.referenceRoots(file) }
  }
}
