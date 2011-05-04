/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

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

  @Override
  public Collection<VirtualFile> additionalRoots(Project project) {
    return Collections.emptyList();
  }
}
