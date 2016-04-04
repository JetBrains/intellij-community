/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.converters.values;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
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

  public static ClassValueConverter getClassValueConverter() {
    return ServiceManager.getService(ClassValueConverter.class);
  }

  public PsiClass fromString(@Nullable @NonNls String s, final ConvertContext context) {
    if (s == null) return null;
    final Module module = context.getModule();
    final PsiFile psiFile = context.getFile();
    final Project project = psiFile.getProject();
    return DomJavaUtil.findClass(s, context.getFile(), context.getModule(), getScope(project, module, psiFile));
  }

  public String toString(@Nullable PsiClass psiClass, final ConvertContext context) {
    return psiClass == null ? null : psiClass.getQualifiedName();
  }

  @NotNull
  public abstract PsiReference[] createReferences(GenericDomValue genericDomValue, PsiElement element, ConvertContext context);

  public static GlobalSearchScope getScope(Project project, @Nullable Module module, @Nullable PsiFile psiFile) {
    if (module == null || psiFile == null) {
      return ProjectScope.getAllScope(project);
    }

    VirtualFile file = psiFile.getOriginalFile().getVirtualFile();
    if (file == null) {
      return ProjectScope.getAllScope(project);
    }
    final boolean inTests = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(file);

    return module.getModuleRuntimeScope(inTests);
  }
}
