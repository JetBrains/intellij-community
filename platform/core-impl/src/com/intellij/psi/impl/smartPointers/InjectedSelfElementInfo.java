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
import com.intellij.lang.LanguageUtil;
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

class InjectedSelfElementInfo extends SmartPointerElementInfo {
  private final SmartPsiFileRange myInjectedFileRangeInHostFile;
  @Nullable private final AffixOffsets myAffixOffsets;
  private final Identikit myType;
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
    myType = Identikit.fromPsi(injectedElement, LanguageUtil.getRootLanguage(containingFile));

    int startAffixIndex = -1;
    int startAffixOffset = -1;
    int endAffixIndex = -1;
    int endAffixOffset = -1;
    List<TextRange> fragments = InjectedLanguageManager.getInstance(project).getNonEditableFragments((DocumentWindow)containingFile.getViewProvider().getDocument());
    for (int i = 0; i < fragments.size(); i++) {
      TextRange range = fragments.get(i);
      if (range.containsOffset(injectedRange.getStartOffset())) {
        startAffixIndex = i;
        startAffixOffset = injectedRange.getStartOffset() - range.getStartOffset();
      }
      if (range.containsOffset(injectedRange.getEndOffset())) {
        endAffixIndex = i;
        endAffixOffset = injectedRange.getEndOffset() - range.getStartOffset();
      }
    }
    myAffixOffsets = startAffixIndex >= 0 || endAffixIndex >= 0 ? new AffixOffsets(startAffixIndex, startAffixOffset, endAffixIndex, endAffixOffset) : null;
  }

  @Override
  VirtualFile getVirtualFile() {
    PsiElement element = restoreElement();
    if (element == null) return null;
    return element.getContainingFile().getVirtualFile();
  }

  @Override
  Segment getRange() {
    return getInjectedRange(false);
  }

  @Nullable
  @Override
  Segment getPsiRange() {
    return getInjectedRange(true);
  }

  @Override
  PsiElement restoreElement() {
    PsiFile hostFile = myHostContext.getContainingFile();
    if (hostFile == null || !hostFile.isValid()) return null;

    PsiElement hostContext = myHostContext.getElement();
    if (hostContext == null) return null;

    Segment segment = myInjectedFileRangeInHostFile.getPsiRange();
    if (segment == null) return null;

    PsiFile injectedPsi = getInjectedFileIn(hostContext, hostFile, TextRange.create(segment));
    ProperTextRange rangeInInjected = hostToInjected(true, segment, injectedPsi, myAffixOffsets);
    if (rangeInInjected == null) return null;

    return myType.findPsiElement(injectedPsi, rangeInInjected.getStartOffset(), rangeInInjected.getEndOffset());
  }

  private PsiFile getInjectedFileIn(@NotNull final PsiElement hostContext,
                                    @NotNull final PsiFile hostFile,
                                    @NotNull final TextRange rangeInHostFile) {
    final PsiDocumentManagerBase docManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(getProject());
    final PsiFile[] result = {null};
    final PsiLanguageInjectionHost.InjectedPsiVisitor visitor = (injectedPsi, places) -> {
      Document document = docManager.getDocument(injectedPsi);
      if (document instanceof DocumentWindow) {
        DocumentWindow window = (DocumentWindow)docManager.getLastCommittedDocument(document);
        TextRange hostRange = window.injectedToHost(new TextRange(0, injectedPsi.getTextLength()));
        if (hostRange.contains(rangeInHostFile)) {
         result[0] = injectedPsi;
        }
      }
    };

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(hostFile);
    if (document != null && documentManager.isUncommited(document)) {
      for (DocumentWindow documentWindow : InjectedLanguageManager.getInstance(getProject()).getCachedInjectedDocumentsInRange(hostFile, rangeInHostFile)) {
        PsiFile injected = documentManager.getPsiFile(documentWindow);
        if (injected != null) {
          visitor.visit(injected, Collections.emptyList());
        }
      }
    }
    else {
      List<Pair<PsiElement,TextRange>> injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(hostContext);
      if (injected != null) {
        for (Pair<PsiElement, TextRange> pair : injected) {
          PsiFile injectedFile = pair.first.getContainingFile();
          visitor.visit(injectedFile, ContainerUtil.emptyList());
        }
      }
    }

    return result[0];
  }

  @Override
  boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other) {
    if (getClass() != other.getClass()) return false;
    if (!((InjectedSelfElementInfo)other).myHostContext.equals(myHostContext)) return false;
    SmartPointerElementInfo myElementInfo = ((SmartPsiElementPointerImpl)myInjectedFileRangeInHostFile).getElementInfo();
    SmartPointerElementInfo oElementInfo = ((SmartPsiElementPointerImpl)((InjectedSelfElementInfo)other).myInjectedFileRangeInHostFile).getElementInfo();
    return myElementInfo.pointsToTheSameElementAs(oElementInfo);
  }

  @Override
  PsiFile restoreFile() {
    PsiFile hostFile = myHostContext.getContainingFile();
    if (hostFile == null || !hostFile.isValid()) return null;

    PsiElement hostContext = myHostContext.getElement();
    if (hostContext == null) return null;

    Segment segment = myInjectedFileRangeInHostFile.getPsiRange();
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

    return hostToInjected(psi, hostElementRange, restoreFile(), myAffixOffsets);
  }

  @Nullable
  private static ProperTextRange hostToInjected(boolean psi, Segment hostRange, @Nullable PsiFile injectedFile, @Nullable AffixOffsets affixOffsets) {
    VirtualFile virtualFile = injectedFile == null ? null : injectedFile.getVirtualFile();
    if (virtualFile instanceof VirtualFileWindow) {
      Project project = injectedFile.getProject();
      DocumentWindow documentWindow = ((VirtualFileWindow)virtualFile).getDocumentWindow();
      if (psi) {
        documentWindow = (DocumentWindow) ((PsiDocumentManagerBase) PsiDocumentManager.getInstance(project)).getLastCommittedDocument(documentWindow);
      }
      int start = documentWindow.hostToInjected(hostRange.getStartOffset());
      int end = documentWindow.hostToInjected(hostRange.getEndOffset());
      if (affixOffsets != null) {
        return affixOffsets.expandRangeToAffixes(start, end, InjectedLanguageManager.getInstance(project).getNonEditableFragments(documentWindow));
      }
      return ProperTextRange.create(start, end);
    }

    return null;
  }

  @Override
  void cleanup() {
    SmartPointerManager.getInstance(getProject()).removePointer(myInjectedFileRangeInHostFile);
  }

  @Nullable
  @Override
  Document getDocumentToSynchronize() {
    return ((SmartPsiElementPointerImpl)myHostContext).getElementInfo().getDocumentToSynchronize();
  }

  @Override
  int elementHashCode() {
    return ((SmartPsiElementPointerImpl)myHostContext).getElementInfo().elementHashCode();
  }

  @NotNull
  @Override
  Project getProject() {
    return myHostContext.getProject();
  }

  @Override
  public String toString() {
    return "injected{type=" + myType + ", range=" + myInjectedFileRangeInHostFile + ", host=" + myHostContext + "}";
  }

  private static class AffixOffsets {
    final int startAffixIndex;
    final int startAffixOffset;
    final int endAffixIndex;
    final int endAffixOffset;

    AffixOffsets(int startAffixIndex, int startAffixOffset, int endAffixIndex, int endAffixOffset) {
      this.startAffixIndex = startAffixIndex;
      this.startAffixOffset = startAffixOffset;
      this.endAffixIndex = endAffixIndex;
      this.endAffixOffset = endAffixOffset;
    }

    @Nullable
    ProperTextRange expandRangeToAffixes(int start, int end, List<TextRange> fragments) {
      if (startAffixIndex >= 0) {
        TextRange fragment = startAffixIndex < fragments.size() ? fragments.get(startAffixIndex) : null;
        if (fragment == null || startAffixOffset > fragment.getLength()) return null;
        start = fragment.getStartOffset() + startAffixOffset;
      }
      if (endAffixIndex >= 0) {
        TextRange fragment = endAffixIndex < fragments.size() ? fragments.get(endAffixIndex) : null;
        if (fragment == null || endAffixOffset > fragment.getLength()) return null;
        end = fragment.getStartOffset() + endAffixOffset;
      }
      return ProperTextRange.create(start, end);
    }
  }

}
