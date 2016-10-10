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

import com.intellij.core.JavaCoreBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.psi.util.PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT;

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
    if (element instanceof PsiCompiledElement) {
      throw new IncorrectOperationException(JavaCoreBundle.message("psi.error.attempt.to.edit.class.file", element.getContainingFile()));
    }
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
      PsiFile file = reference.getElement().getContainingFile();
      String moduleName = reference.getCanonicalText();
      Collection<PsiJavaModule> modules = findModules(file, moduleName, incompleteCode);
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

    private static Collection<PsiJavaModule> findModules(PsiFile file, String moduleName, boolean incompleteCode) {
      Project project = file.getProject();

      GlobalSearchScope scope = null;
      if (incompleteCode || file.getOriginalFile() instanceof PsiCompiledFile) {
        scope = GlobalSearchScope.allScope(project);
      }
      else {
        VirtualFile vFile = file.getVirtualFile();
        if (vFile != null) {
          Module module = FileIndexFacade.getInstance(project).getModuleForFile(vFile);
          if (module != null) {
            scope = module.getModuleWithDependenciesAndLibrariesScope(false);
          }
        }
      }

      return scope != null ? JavaFileManager.SERVICE.getInstance(project).findModules(moduleName, scope) : Collections.<PsiJavaModule>emptyList();
    }
  }

  private static final Key<ParameterizedCachedValue<PsiJavaModule, Pair<String, Boolean>>> KEY = Key.create("java.module.ref.text.resolve");

  @Nullable
  public static PsiJavaModule resolve(@NotNull final PsiElement refOwner, String refText, boolean incompleteCode) {
    if (StringUtil.isEmpty(refText)) return null;
    CachedValuesManager manager = CachedValuesManager.getManager(refOwner.getProject());
    return manager.getParameterizedCachedValue(refOwner, KEY, new ParameterizedCachedValueProvider<PsiJavaModule, Pair<String, Boolean>>() {
      @Nullable
      @Override
      public CachedValueProvider.Result<PsiJavaModule> compute(Pair<String, Boolean> p) {
        Collection<PsiJavaModule> modules = Resolver.findModules(refOwner.getContainingFile(), p.first, p.second);
        PsiJavaModule module = modules.size() == 1 ? modules.iterator().next() : null;
        return CachedValueProvider.Result.create(module, OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    }, false, pair(refText, incompleteCode));
  }
}