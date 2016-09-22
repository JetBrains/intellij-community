/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiJavaModuleReference extends PsiReferenceBase.Poly<PsiJavaModuleReferenceElement> {
  public PsiJavaModuleReference(@NotNull PsiJavaModuleReferenceElement element) {
    super(element, new TextRange(0, element.getTextLength()), false);
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getElement().getReferenceText();
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    return ResolveCache.getInstance(getProject()).resolveWithCaching(this, Resolver.INSTANCE, false, incompleteCode);
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newName) throws IncorrectOperationException {
    PsiJavaModuleReferenceElement element = getElement();
    PsiElementFactory factory = PsiElementFactory.SERVICE.getInstance(element.getProject());
    PsiJavaModuleReferenceElement newElement = factory.createModuleFromText("module " + newName + " {}").getNameElement();
    return element.replace(newElement);
  }

  private Project getProject() {
    return getElement().getProject();
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver<PsiJavaModuleReference> {
    private static final ResolveCache.PolyVariantResolver<PsiJavaModuleReference> INSTANCE = new Resolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaModuleReference reference, boolean incompleteCode) {
      Project project = reference.getProject();

      GlobalSearchScope scope = null;
      if (!incompleteCode) {
        VirtualFile file = reference.getElement().getContainingFile().getVirtualFile();
        if (file != null) {
          Module module = FileIndexFacade.getInstance(project).getModuleForFile(file);
          if (module != null) {
            scope = module.getModuleWithDependenciesAndLibrariesScope(false);
          }
        }
      }
      else {
        scope = GlobalSearchScope.allScope(project);
      }

      if (scope != null) {
        JavaFileManager service = JavaFileManager.SERVICE.getInstance(project);
        Collection<PsiJavaModule> modules = service.findModules(reference.getCanonicalText(), scope);
        if (!modules.isEmpty()) {
          ResolveResult[] result = new ResolveResult[modules.size()];
          int i = 0;
          for (PsiJavaModule module : modules) result[i++] = new PsiElementResolveResult(module);
          return result;
        }
      }

      return ResolveResult.EMPTY_ARRAY;
    }
  }
}