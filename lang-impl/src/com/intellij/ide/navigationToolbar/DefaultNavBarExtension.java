/*
 * User: anna
 * Date: 04-Feb-2008
 */
package com.intellij.ide.navigationToolbar;

import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

public class DefaultNavBarExtension implements NavBarModelExtension{
  @Nullable
  public String getPresentableText(final Object object) {
    if (object instanceof Project) {
      return ((Project)object).getName();
    }
    else if (object instanceof Module) {
      return ((Module)object).getName();
    }
    else if (object instanceof PsiFile) {
      return ((PsiFile)object).getName();
    }
    else if (object instanceof PsiDirectory) {
      return ((PsiDirectory)object).getVirtualFile().getName();
    }
    else if (object instanceof JdkOrderEntry) {
      return ((JdkOrderEntry)object).getJdkName();
    }
    else if (object instanceof LibraryOrderEntry) {
      final String libraryName = ((LibraryOrderEntry)object).getLibraryName();
      return libraryName != null ? libraryName : AnalysisScopeBundle.message("package.dependencies.library.node.text");
    }
    else if (object instanceof ModuleOrderEntry) {
      final ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)object;
      return moduleOrderEntry.getModuleName();
    }
    return null;
  }

  @Nullable
  public PsiElement getParent(final PsiElement object) {
    return null;
  }

  public PsiElement adjustElement(final PsiElement psiElement) {
    final PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile != null) return containingFile;
    return psiElement;
  }


}