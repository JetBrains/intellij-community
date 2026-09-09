// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.template

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import org.jetbrains.annotations.ApiStatus

/** Supplies presentation rules for Java embedded in a template. */
@ApiStatus.Internal
public interface JavaTemplatePresentationSupport {
  public fun ignoresModification(type: FileType): Boolean = false
  public fun supportsPrinting(type: FileType): Boolean = false
  public fun allowsJavaSource(file: PsiFile): Boolean = true
  public fun hidesClass(psiClass: PsiClass): Boolean = false
  public fun classKind(psiClass: PsiClass): Int? = null
  public fun hierarchyName(member: PsiMember): @NlsSafe String? = null
  public fun showsHierarchyLocation(member: PsiMember): Boolean = true
  public fun showRefactoringError(project: Project, editor: Editor?, @NlsContexts.DialogTitle refactoringName: String,
                                 helpId: String?): Boolean = false

  public companion object {
    @JvmField
    public val EP_NAME: ExtensionPointName<JavaTemplatePresentationSupport> =
      ExtensionPointName.create("com.intellij.java.templatePresentationSupport")

    @JvmStatic
    public fun isModificationIgnored(type: FileType): Boolean = EP_NAME.extensionList.any { it.ignoresModification(type) }

    @JvmStatic
    public fun isPrintingSupported(type: FileType): Boolean = EP_NAME.extensionList.any { it.supportsPrinting(type) }

    @JvmStatic
    public fun isJavaSourceAllowed(file: PsiFile): Boolean = EP_NAME.extensionList.all { it.allowsJavaSource(file) }

    @JvmStatic
    public fun isClassHidden(psiClass: PsiClass): Boolean = EP_NAME.extensionList.any { it.hidesClass(psiClass) }

    @JvmStatic
    public fun getClassKind(psiClass: PsiClass): Int? = EP_NAME.extensionList.firstNotNullOfOrNull { it.classKind(psiClass) }

    @JvmStatic
    public fun getHierarchyName(member: PsiMember): @NlsSafe String? =
      EP_NAME.extensionList.firstNotNullOfOrNull { it.hierarchyName(member) }

    @JvmStatic
    public fun isHierarchyLocationShown(member: PsiMember): Boolean = EP_NAME.extensionList.all { it.showsHierarchyLocation(member) }

    @JvmStatic
    public fun showUnsupportedRefactoringError(project: Project, editor: Editor?, @NlsContexts.DialogTitle refactoringName: String,
                                              helpId: String?) {
      EP_NAME.extensionList.any { it.showRefactoringError(project, editor, refactoringName, helpId) }
    }
  }
}
