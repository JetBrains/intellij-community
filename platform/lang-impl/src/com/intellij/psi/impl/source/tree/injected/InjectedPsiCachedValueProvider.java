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

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
*/
class InjectedPsiCachedValueProvider implements ParameterizedCachedValueProvider<MultiHostRegistrarImpl, PsiElement> {
  @Override
  public CachedValueProvider.Result<MultiHostRegistrarImpl> compute(PsiElement element) {
    PsiFile hostPsiFile = element.getContainingFile();
    if (hostPsiFile == null) return null;
    FileViewProvider viewProvider = hostPsiFile.getViewProvider();
    final DocumentEx hostDocument = (DocumentEx)viewProvider.getDocument();
    if (hostDocument == null) return null;

    PsiManager psiManager = viewProvider.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);

    final MultiHostRegistrarImpl result = doCompute(element, injectedManager, project, hostPsiFile);

    return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
  }

  @Nullable
  static MultiHostRegistrarImpl doCompute(@NotNull final PsiElement element,
                                          @NotNull InjectedLanguageManagerImpl injectedManager,
                                          Project project,
                                          PsiFile hostPsiFile) {
    MyInjProcessor processor = new MyInjProcessor(project, hostPsiFile);
    injectedManager.processInPlaceInjectorsFor(element, processor);
    MultiHostRegistrarImpl registrar = processor.hostRegistrar;
    return registrar == null || registrar.getResult() == null ? null : registrar;
  }

  private static class MyInjProcessor implements InjectedLanguageManagerImpl.InjProcessor {
    private MultiHostRegistrarImpl hostRegistrar;
    private final Project myProject;
    private final PsiFile myHostPsiFile;

    private MyInjProcessor(Project project, PsiFile hostPsiFile) {
      myProject = project;
      myHostPsiFile = hostPsiFile;
    }

    @Override
    public boolean process(@NotNull PsiElement element, @NotNull MultiHostInjector injector) {
      if (hostRegistrar == null) {
        hostRegistrar = new MultiHostRegistrarImpl(myProject, myHostPsiFile, element);
      }
      injector.getLanguagesToInject(hostRegistrar, element);
      List<Pair<Place,PsiFile>> result = hostRegistrar.getResult();
      return result == null;
    }
  }
}
