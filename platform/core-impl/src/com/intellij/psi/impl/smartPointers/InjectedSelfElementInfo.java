/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi.impl.smartPointers;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
* User: cdr
*/
class InjectedSelfElementInfo extends SelfElementInfo {
  private final SmartPsiFileRange myInjectedFileRangeInHostFile;
  private final Class<? extends PsiElement> anchorClass;
  private final Language anchorLanguage;

  InjectedSelfElementInfo(@NotNull Project project, @NotNull PsiElement element, @NotNull PsiElement hostContext) {
    super(project, hostContext);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    TextRange range = InjectedLanguageManager.getInstance(project).injectedToHost(element, element.getTextRange());
    myInjectedFileRangeInHostFile = smartPointerManager.createSmartPsiFileRangePointer(hostContext.getContainingFile(), range);
    anchorClass = element.getClass();
    anchorLanguage = element.getContainingFile().getLanguage();
  }

  @Override
  public VirtualFile getVirtualFile() {
    PsiElement element = restoreElement();
    if (element == null) return null;
    return element.getContainingFile().getVirtualFile();
  }

  @Override
  public PsiElement restoreElement() {
    PsiElement hostContext = super.restoreElement();
    if (hostContext == null) return null;

    Segment segment = myInjectedFileRangeInHostFile.getRange();
    if (segment == null) return null;
    final TextRange rangeInHostFile = TextRange.create(segment);
    final Ref<PsiElement> result = new Ref<PsiElement>();

    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
    PsiFile hostFile = hostContext.getContainingFile();
    if (hostFile == null) return null;

    PsiLanguageInjectionHost.InjectedPsiVisitor visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        if (result.get() != null) return;
        TextRange hostRange = manager.injectedToHost(injectedPsi, new TextRange(0, injectedPsi.getTextLength()));
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(injectedPsi);
        if (hostRange.contains(rangeInHostFile) && document instanceof DocumentWindow) {
          int start = ((DocumentWindow)document).hostToInjected(rangeInHostFile.getStartOffset());
          int end = ((DocumentWindow)document).hostToInjected(rangeInHostFile.getEndOffset());
          PsiElement element = findElementInside(injectedPsi, start, end, anchorClass, anchorLanguage);
          result.set(element);
        }
      }
    };

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(hostFile);
    if (document != null && documentManager.isUncommited(document)) {
      for (DocumentWindow documentWindow : InjectedLanguageManager.getInstance(getProject()).getCachedInjectedDocuments(hostFile)) {
        PsiFile injected = documentManager.getPsiFile(documentWindow);
        if (injected != null) {
          visitor.visit(injected, Collections.<PsiLanguageInjectionHost.Shred>emptyList());
        }
      }
    }
    else {
      List<Pair<PsiElement,TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(hostContext);
      if (injected != null) {
        for (Pair<PsiElement, TextRange> pair : injected) {
          PsiFile injectedFile = pair.first.getContainingFile();
          visitor.visit(injectedFile, ContainerUtil.<PsiLanguageInjectionHost.Shred>emptyList());
        }
      }
    }

    return result.get();
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    if (getClass() != other.getClass()) return false;
    if (!super.pointsToTheSameElementAs(other)) return false;
    SmartPointerElementInfo myElementInfo = ((SmartPsiElementPointerImpl)myInjectedFileRangeInHostFile).getElementInfo();
    SmartPointerElementInfo oElementInfo = ((SmartPsiElementPointerImpl)((InjectedSelfElementInfo)other).myInjectedFileRangeInHostFile).getElementInfo();
    return myElementInfo.pointsToTheSameElementAs(oElementInfo);
  }

  @Override
  public void cleanup() {
    super.cleanup();
    SmartPointerManager.getInstance(getProject()).removePointer(myInjectedFileRangeInHostFile);
  }
}
