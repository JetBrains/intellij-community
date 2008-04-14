/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClassValueConverter extends Converter<PsiClass> implements CustomReferenceConverter {

  public static ClassValueConverter getClassValueConverter(Project project) {
    return ServiceManager.getService(project, ClassValueConverter.class);
  }

  public PsiClass fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    final Module module = context.getModule();
    final PsiFile psiFile = context.getFile();
    final Project project = psiFile.getProject();
    return DomJavaUtil.findClass(s, context.getFile(), context.getModule(), getScope(project, module, psiFile));
  }

  public String toString(@Nullable PsiClass psiClass, final ConvertContext context) {
    return psiClass == null? null : psiClass.getQualifiedName();
  }

  @NotNull
  public abstract PsiReference[] createReferences(GenericDomValue genericDomValue, PsiElement element, ConvertContext context);

  public static GlobalSearchScope getScope(Project project, @Nullable Module module, @Nullable PsiFile psiFile) {
    if (module == null || psiFile == null) {
      return ProjectScope.getAllScope(project);
     }
     VirtualFile file = psiFile.getVirtualFile();
     if (file == null) {
       final PsiFile originalFile = psiFile.getOriginalFile();
       if (originalFile != null) {
         file = originalFile.getVirtualFile();
       }
     }
     if (file == null) {
       return ProjectScope.getAllScope(project);
     }
     final boolean inTests = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(file);

     return module.getModuleWithDependenciesAndLibrariesScope(inTests);
  }
}
