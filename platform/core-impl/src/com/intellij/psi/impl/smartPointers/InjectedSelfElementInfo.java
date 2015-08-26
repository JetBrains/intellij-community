/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FreeThreadedFileViewProvider;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
* User: cdr
*/
class InjectedSelfElementInfo extends SmartPointerElementInfo {
  private final SmartPsiFileRange myInjectedFileRangeInHostFile;
  private final Class<? extends PsiElement> anchorClass;
  private final Language anchorLanguage;
  @NotNull
  private final SmartPsiElementPointer<PsiLanguageInjectionHost> myHostContext;

  InjectedSelfElementInfo(@NotNull Project project,
                          @NotNull PsiElement injectedElement,
                          @NotNull TextRange injectedRange,
                          @NotNull PsiFile containingFile,
                          @NotNull SmartPsiElementPointer<PsiLanguageInjectionHost> hostContext) {
    myHostContext = hostContext;
    assert containingFile.getViewProvider() instanceof FreeThreadedFileViewProvider : "element parameter must be an injected element: "+injectedElement+"; "+containingFile;
    assert containingFile.getTextRange().contains(injectedRange) : "Injected range outside the file: "+injectedRange +"; file: "+containingFile.getTextRange();

    TextRange hostRange = InjectedLanguageManager.getInstance(project).injectedToHost(injectedElement, injectedRange);
    PsiFile hostFile = hostContext.getContainingFile();
    assert !(hostFile.getViewProvider() instanceof FreeThreadedFileViewProvider) : "hostContext parameter must not be and injected element: "+hostContext;
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    myInjectedFileRangeInHostFile = smartPointerManager.createSmartPsiFileRangePointer(hostFile, hostRange);
    anchorLanguage = containingFile.getLanguage();
    anchorClass = injectedElement.getClass(); //containingFile.findElementAt(injectedRange.getStartOffset()).getClass();
  }

  @Override
  public VirtualFile getVirtualFile() {
    PsiElement element = restoreElement();
    if (element == null) return null;
    return element.getContainingFile().getVirtualFile();
  }

  @Override
  public Segment getRange() {
    return getInjectedRange(false);
  }

  @Nullable
  @Override
  public Segment getPsiRange() {
    return getInjectedRange(true);
  }

  @Override
  public PsiElement restoreElement() {
    PsiFile hostFile = myHostContext.getContainingFile();
    if (hostFile == null || !hostFile.isValid()) return null;

    PsiElement hostContext = myHostContext.getElement();
    if (hostContext == null) return null;

    Segment segment = myInjectedFileRangeInHostFile.getPsiRange();
    if (segment == null) return null;

    PsiFile injectedPsi = getInjectedFileIn(hostContext, hostFile, TextRange.create(segment));
    ProperTextRange rangeInInjected = hostToInjected(true, segment, injectedPsi);
    if (rangeInInjected == null) return null;

    return SelfElementInfo.findElementInside(injectedPsi, rangeInInjected.getStartOffset(), rangeInInjected.getEndOffset(), anchorClass, anchorLanguage);
  }

  private PsiFile getInjectedFileIn(@NotNull final PsiElement hostContext,
                                    @NotNull final PsiFile hostFile,
                                    @NotNull final TextRange rangeInHostFile) {
    final InjectedLanguageManager manager = InjectedLanguageManager.getInstance(getProject());
    final PsiFile[] result = {null};
    final PsiLanguageInjectionHost.InjectedPsiVisitor visitor = new PsiLanguageInjectionHost.InjectedPsiVisitor() {
      @Override
      public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
        TextRange hostRange = manager.injectedToHost(injectedPsi, new TextRange(0, injectedPsi.getTextLength()));
        Document document = PsiDocumentManager.getInstance(getProject()).getDocument(injectedPsi);
        if (hostRange.contains(rangeInHostFile) && document instanceof DocumentWindow) {
         result[0] = injectedPsi;
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

    return result[0];
  }

  @Override
  public boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    if (getClass() != other.getClass()) return false;
    if (!(((InjectedSelfElementInfo)other).myHostContext).equals(myHostContext)) return false;
    SmartPointerElementInfo myElementInfo = ((SmartPsiElementPointerImpl)myInjectedFileRangeInHostFile).getElementInfo();
    SmartPointerElementInfo oElementInfo = ((SmartPsiElementPointerImpl)((InjectedSelfElementInfo)other).myInjectedFileRangeInHostFile).getElementInfo();
    return myElementInfo.pointsToTheSameElementAs(oElementInfo);
  }

  @Override
  public PsiFile restoreFile() {
    PsiFile hostFile = myHostContext.getContainingFile();
    if (hostFile == null || !hostFile.isValid()) return null;

    PsiElement hostContext = myHostContext.getElement();
    if (hostContext == null) return null;

    Segment segment = myInjectedFileRangeInHostFile.getRange();
    if (segment == null) return null;
    final TextRange rangeInHostFile = TextRange.create(segment);
    return getInjectedFileIn(hostContext, hostFile, rangeInHostFile);
  }

  @Nullable
  private ProperTextRange getInjectedRange(boolean psi) {
    PsiElement hostContext = myHostContext.getElement();
    if (hostContext == null) return null;

    Segment hostElementRange = psi ? myInjectedFileRangeInHostFile.getPsiRange() : myInjectedFileRangeInHostFile.getRange();
    if (hostElementRange == null) return null;

    return hostToInjected(psi, hostElementRange, restoreFile());
  }

  @Nullable
  private ProperTextRange hostToInjected(boolean psi, Segment hostRange, @Nullable PsiFile injectedFile) {
    VirtualFile virtualFile = injectedFile == null ? null : injectedFile.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      DocumentWindow documentWindow = ((VirtualFileWindow)virtualFile).getDocumentWindow();
      if (psi) {
        documentWindow = (DocumentWindow) ((PsiDocumentManagerBase) PsiDocumentManager.getInstance(getProject())).getLastCommittedDocument(documentWindow);
      }
      int start = documentWindow.hostToInjected(hostRange.getStartOffset());
      int end = documentWindow.hostToInjected(hostRange.getEndOffset());
      return ProperTextRange.create(start, end);
    }

    return null;
  }

  @Override
  public void cleanup() {
    SmartPointerManager.getInstance(getProject()).removePointer(myInjectedFileRangeInHostFile);
  }

  @Nullable
  @Override
  public Document getDocumentToSynchronize() {
    return ((SmartPsiElementPointerImpl)myHostContext).getElementInfo().getDocumentToSynchronize();
  }

  @Override
  public int elementHashCode() {
    return ((SmartPsiElementPointerImpl)myHostContext).getElementInfo().elementHashCode();
  }

  @NotNull
  @Override
  public Project getProject() {
    return myHostContext.getProject();
  }
}
