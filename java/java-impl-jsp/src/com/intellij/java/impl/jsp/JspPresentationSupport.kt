// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.jsp

import com.intellij.java.JavaBundle
import com.intellij.java.impl.template.JavaTemplatePresentationSupport
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecordComponent
import com.intellij.psi.ServerPageFile
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.psi.impl.source.jsp.jspJava.JspClass
import com.intellij.psi.util.FileTypeUtils
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.xml.util.JspFileTypeUtil

internal class JspPresentationSupport : JavaTemplatePresentationSupport {
  override fun ignoresModification(type: FileType): Boolean = JspFileTypeUtil.isJspOrJspX(type)
  override fun supportsPrinting(type: FileType): Boolean = JspFileTypeUtil.isJsp(type)
  override fun allowsJavaSource(file: PsiFile): Boolean = file !is ServerPageFile
  override fun hidesClass(psiClass: PsiClass): Boolean = psiClass.parent is PsiFile && FileTypeUtils.isInServerPageFile(psiClass)
  override fun classKind(psiClass: PsiClass): Int? = if (psiClass is JspClass) ElementPresentationUtil.CLASS_KIND_JSP else null

  override fun hierarchyName(member: PsiMember): @NlsSafe String? {
    if (!FileTypeUtils.isInServerPageFile(member)) return null
    if (member is PsiMethod || member is PsiField || member is PsiRecordComponent) {
      return member.containingFile?.name ?: JavaBundle.message("node.call.hierarchy.unknown.jsp")
    }
    return if (member is PsiFile) PsiUtilCore.getTemplateLanguageFile(member).name else null
  }

  override fun showsHierarchyLocation(member: PsiMember): Boolean =
    !(FileTypeUtils.isInServerPageFile(member) && member is PsiFile)

  override fun showRefactoringError(project: Project, editor: Editor?, @NlsContexts.DialogTitle refactoringName: String,
                                    helpId: String?): Boolean {
    val message = RefactoringBundle.getCannotRefactorMessage(JavaRefactoringBundle.message("refactoring.is.not.supported.for.jsp.classes"))
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId)
    return true
  }
}
