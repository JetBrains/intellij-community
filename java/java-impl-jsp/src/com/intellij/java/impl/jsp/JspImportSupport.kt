// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.jsp

import com.intellij.java.impl.template.JavaTemplateImportSupport
import com.intellij.jsp.JspSpiUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.JspPsiUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiImportList
import com.intellij.psi.PsiImportStatementBase
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.ServerPageFile
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportList
import com.intellij.psi.impl.source.jsp.jspJava.JspxImportStatement
import com.intellij.psi.jsp.JspFile
import com.intellij.psi.util.FileTypeUtils
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet

internal class JspImportSupport : JavaTemplateImportSupport {
  override fun createContext(file: PsiJavaFile): JavaTemplateImportSupport.Context? {
    val context = JspPsiUtil.getJspFile(file) ?: return null
    return object : JavaTemplateImportSupport.Context {
      override val files: List<PsiJavaFile> = buildList {
        add(file)
        for (relatedFile in JspSpiUtil.getIncludingFiles(context) + JspSpiUtil.getIncludedFiles(context)) {
          val javaRoot = relatedFile.viewProvider.getPsi(JavaLanguage.INSTANCE)
          if (javaRoot is PsiJavaFile && javaRoot !== file) add(javaRoot)
        }
      }

      override fun allowsImport(resolveScope: PsiElement?, resolvedElement: PsiElement?): Boolean {
        return resolvedElement == null ||
               (resolveScope == null || resolveScope.isValid) &&
               (resolveScope !is JspxImportStatement || context === resolveScope.declarationFile)
      }
    }
  }

  override fun replaceImportList(oldList: PsiImportList, newList: PsiImportList): Boolean {
    if (oldList !is JspxImportList) return false
    oldList.replace(newList)
    return true
  }

  override fun findRedundantImports(file: PsiJavaFile,
                                   imports: Array<PsiImportStatementBase>,
                                   uniqueImports: Set<PsiImportStatementBase>): Collection<PsiImportStatementBase>? {
    if (!FileTypeUtils.isInServerPageFile(file)) return null
    val redundant = ReferenceOpenHashSet<PsiImportStatementBase>()
    redundant.addAll(imports.asList())
    redundant.removeAll(uniqueImports)
    for (statement in imports) {
      if (statement is JspxImportStatement && statement.isForeignFileImport) redundant.remove(statement)
    }
    return redundant
  }

  override fun allowsImportOptimizer(file: PsiFile): Boolean = file !is JspFile
  override fun allowsOnTheFlyOptimization(file: PsiFile): Boolean = file !is ServerPageFile
}
