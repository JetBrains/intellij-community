// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.template

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import org.jetbrains.annotations.ApiStatus

/** Supplies import operations for Java embedded in a template. */
@ApiStatus.Internal
public interface JavaTemplateImportSupport {
  public fun createContext(file: PsiJavaFile): Context? = null
  public fun replaceImportList(oldList: PsiImportList, newList: PsiImportList): Boolean = false
  public fun findRedundantImports(file: PsiJavaFile,
                                 imports: Array<PsiImportStatementBase>,
                                 uniqueImports: Set<PsiImportStatementBase>): Collection<PsiImportStatementBase>? = null
  public fun allowsImportOptimizer(file: PsiFile): Boolean = true
  public fun allowsOnTheFlyOptimization(file: PsiFile): Boolean = true

  /** Keeps the roots and the import filter together for one collection operation. */
  public interface Context {
    public val files: List<PsiJavaFile>
    public fun allowsImport(resolveScope: PsiElement?, resolvedElement: PsiElement?): Boolean
  }

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<JavaTemplateImportSupport> =
      ExtensionPointName.create("com.intellij.java.templateImportSupport")

    @JvmStatic
    public fun getContexts(file: PsiJavaFile): List<Context> = EP_NAME.extensionList.mapNotNull { it.createContext(file) }

    @JvmStatic
    public fun replaceImports(oldList: PsiImportList, newList: PsiImportList): Boolean =
      EP_NAME.extensionList.any { it.replaceImportList(oldList, newList) }

    @JvmStatic
    public fun getRedundantImports(file: PsiJavaFile,
                                   imports: Array<PsiImportStatementBase>,
                                   uniqueImports: Set<PsiImportStatementBase>): Collection<PsiImportStatementBase>? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.findRedundantImports(file, imports, uniqueImports) }

    @JvmStatic
    public fun isImportOptimizerAllowed(file: PsiFile): Boolean = EP_NAME.extensionList.all { it.allowsImportOptimizer(file) }

    @JvmStatic
    public fun isOnTheFlyOptimizationAllowed(file: PsiFile): Boolean = EP_NAME.extensionList.all { it.allowsOnTheFlyOptimization(file) }
  }
}
