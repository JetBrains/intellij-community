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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PsiJavaModuleReference extends PsiReferenceBase.Poly<PsiJavaModuleReferenceElement> {
  public PsiJavaModuleReference(@NotNull PsiJavaModuleReferenceElement element) {
    super(element);
  }

  @Override
  protected TextRange calculateDefaultRangeInElement() {
    return new TextRange(0, getElement().getTextLength());
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

  private Project getProject() {
    return getElement().getProject();
  }

  private static class Resolver implements ResolveCache.PolyVariantResolver<PsiJavaModuleReference> {
    private static final ResolveCache.PolyVariantResolver<PsiJavaModuleReference> INSTANCE = new Resolver();

    @NotNull
    @Override
    public ResolveResult[] resolve(@NotNull PsiJavaModuleReference reference, boolean incompleteCode) {
      Project project = reference.getProject();
      JavaFileManager service = ServiceManager.getService(project, JavaFileManager.class);
      Collection<PsiJavaModule> modules = service.findModules(reference.getCanonicalText());
      if (!modules.isEmpty()) {
        ResolveResult[] result = new ResolveResult[modules.size()];
        int i = 0;
        for (PsiJavaModule module : modules) result[i++] = new PsiElementResolveResult(module);
        return result;
      }
      else {
        return ResolveResult.EMPTY_ARRAY;
      }
    }
  }
}