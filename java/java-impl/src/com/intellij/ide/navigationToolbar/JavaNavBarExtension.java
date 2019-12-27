// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.navigationToolbar;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.jrt.JrtFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageViewShortNameLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/**
 * @author anna
 */
public class JavaNavBarExtension extends AbstractNavBarModelExtension {
  @Override
  public String getPresentableText(final Object object) {
    if (object instanceof PsiMember) {
      return ElementDescriptionUtil.getElementDescription((PsiElement)object, UsageViewShortNameLocation.INSTANCE);
    }
    else if (object instanceof PsiPackage) {
      final String name = ((PsiPackage)object).getName();
      return name != null ? name : AnalysisScopeBundle.message("dependencies.tree.node.default.package.abbreviation");
    }
    else if (object instanceof PsiDirectory && JrtFileSystem.isRoot(((PsiDirectory)object).getVirtualFile())) {
      return LangBundle.message("jrt.node.short");
    }
    return null;
  }

  @Override
  public PsiElement getParent(final PsiElement psiElement) {
    if (psiElement instanceof PsiPackage) {
      final PsiPackage parentPackage = ((PsiPackage)psiElement).getParentPackage();
      if (parentPackage != null && parentPackage.getQualifiedName().length() > 0) {
        return parentPackage;
      }
    }
    if (psiElement instanceof PsiMember) {
      return PsiTreeUtil.getParentOfType(psiElement, PsiClass.class, PsiMethod.class);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement adjustElement(@NotNull final PsiElement psiElement) {
    final ProjectFileIndex index = ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex();
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) {
      final VirtualFile file = containingFile.getVirtualFile();
      if (file != null &&
          (index.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) || index.isInLibrary(file))) {
        if (psiElement instanceof PsiJavaFile) {
          final PsiJavaFile psiJavaFile = (PsiJavaFile)psiElement;
          if (psiJavaFile.getViewProvider().getBaseLanguage() == JavaLanguage.INSTANCE) {
            final PsiClass[] psiClasses = psiJavaFile.getClasses();
            if (psiClasses.length == 1) {
              return psiClasses[0];
            }
          }
        }
        if (!Registry.is("navBar.show.members") && psiElement instanceof PsiClass) {
          return psiElement;
        }
      }
      if (!Registry.is("navBar.show.members")) {
        return containingFile;
      }
    }
    return psiElement;
  }

  @Nullable
  @Override
  public PsiElement getLeafElement(@NotNull DataContext dataContext) {
    if (Registry.is("navBar.show.members")) {
      PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
      Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
      if (psiFile == null || editor == null) return null;
      PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
      if (psiElement != null && psiElement.getLanguage() == JavaLanguage.INSTANCE) {
        return PsiTreeUtil.getParentOfType(psiElement, PsiMember.class);
      }
    }
    return null;
  }
}
