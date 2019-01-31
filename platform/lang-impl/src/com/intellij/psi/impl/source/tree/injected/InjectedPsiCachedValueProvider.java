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

import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;

class InjectedPsiCachedValueProvider implements ParameterizedCachedValueProvider<InjectionResult, PsiElement> {
  @Override
  public CachedValueProvider.Result<InjectionResult> compute(PsiElement element) {
    PsiFile hostPsiFile = element.getContainingFile();
    if (hostPsiFile == null) return null;
    FileViewProvider viewProvider = hostPsiFile.getViewProvider();
    if (viewProvider instanceof InjectedFileViewProvider) return null; // no injection inside injection
    final DocumentEx hostDocument = (DocumentEx)viewProvider.getDocument();
    if (hostDocument == null) return null;

    PsiManager psiManager = viewProvider.getManager();
    final Project project = psiManager.getProject();
    InjectedLanguageManagerImpl injectedManager = InjectedLanguageManagerImpl.getInstanceImpl(project);

    InjectionResult result = injectedManager.processInPlaceInjectorsFor(hostPsiFile, element);

    return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT, hostDocument);
  }
}
