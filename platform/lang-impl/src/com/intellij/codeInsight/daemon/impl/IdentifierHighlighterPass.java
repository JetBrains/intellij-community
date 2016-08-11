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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class IdentifierHighlighterPass extends TextEditorHighlightingPass {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass");

  private final PsiFile myFile;
  private final Editor myEditor;
  private final Collection<TextRange> myReadAccessRanges = Collections.synchronizedList(new ArrayList<TextRange>());
  private final Collection<TextRange> myWriteAccessRanges = Collections.synchronizedList(new ArrayList<TextRange>());
  private final int myCaretOffset;

  protected IdentifierHighlighterPass(@NotNull Project project, @NotNull PsiFile file, @NotNull Editor editor) {
    super(project, editor.getDocument(), false);
    myFile = file;
    myEditor = editor;
    myCaretOffset = myEditor.getCaretModel().getOffset();
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    @SuppressWarnings("unchecked") HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(myEditor, myFile);
    if (handler != null) {
      List<PsiElement> targets = handler.getTargets();
      handler.computeUsages(targets);
      final List<TextRange> readUsages = handler.getReadUsages();
      for (TextRange readUsage : readUsages) {
        LOG.assertTrue(readUsage != null, "null text range from " + handler);
      }
      myReadAccessRanges.addAll(readUsages);
      final List<TextRange> writeUsages = handler.getWriteUsages();
      for (TextRange writeUsage : writeUsages) {
        LOG.assertTrue(writeUsage != null, "null text range from " + handler);
      }
      myWriteAccessRanges.addAll(writeUsages);
      if (!handler.highlightReferences()) return;
    }

    int flags = TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement myTarget;
    try {
      myTarget = TargetElementUtil.getInstance().findTargetElement(myEditor, flags, myCaretOffset);
    }
    catch (IndexNotReadyException e) {
      return;
    }
    
    if (myTarget == null) {
      if (!PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        // when document is committed, try to check injected stuff - it's fast
        Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile, myCaretOffset);
        myTarget = TargetElementUtil.getInstance().findTargetElement(injectedEditor, flags, injectedEditor.getCaretModel().getOffset());
      }
    }
    
    if (myTarget != null) {
      highlightTargetUsages(myTarget);
    } else {
      PsiReference ref = TargetElementUtil.findReference(myEditor);
      if (ref instanceof PsiPolyVariantReference) {
        if (!ref.getElement().isValid()) {
          throw new PsiInvalidElementAccessException(ref.getElement(), "Invalid element in " + ref + " of " + ref.getClass() + "; editor=" + myEditor);
        }
        ResolveResult[] results = ((PsiPolyVariantReference)ref).multiResolve(false);
        if (results.length > 0) {
          for (ResolveResult result : results) {
            PsiElement target = result.getElement();
            if (target != null) {
              if (!target.isValid()) {
                throw new PsiInvalidElementAccessException(target, "Invalid element returned from " + ref + " of " + ref.getClass() + "; editor=" + myEditor);
              }
              highlightTargetUsages(target);
            }
          }
        }
      }

    }
  }

  /**
   * Returns read and write usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   * @return a pair where first element is read usages and second is write usages
   */
  @NotNull
  public static Couple<Collection<TextRange>> getHighlightUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    return getUsages(target, psiElement, withDeclarations, true);
  }

  /**
   * Returns usages of psi element inside a single element
   *
   * @param target target psi element
   * @param psiElement psi element to search in
   */
  @NotNull
  public static Collection<TextRange> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations) {
    return getUsages(target, psiElement, withDeclarations, false).first;
  }

  @NotNull
  private static Couple<Collection<TextRange>> getUsages(@NotNull PsiElement target, PsiElement psiElement, boolean withDeclarations, boolean detectAccess) {
    List<TextRange> readRanges = new ArrayList<>();
    List<TextRange> writeRanges = new ArrayList<>();
    final ReadWriteAccessDetector detector = detectAccess ? ReadWriteAccessDetector.findDetector(target) : null;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(target.getProject())).getFindUsagesManager();
    final FindUsagesHandler findUsagesHandler = findUsagesManager.getFindUsagesHandler(target, true);
    final LocalSearchScope scope = new LocalSearchScope(psiElement);
    Collection<PsiReference> refs = findUsagesHandler != null
                              ? findUsagesHandler.findReferencesToHighlight(target, scope)
                              : ReferencesSearch.search(target, scope).findAll();
    for (PsiReference psiReference : refs) {
      if (psiReference == null) {
        LOG.error("Null reference returned, findUsagesHandler=" + findUsagesHandler + "; target=" + target + " of " + target.getClass());
        continue;
      }
      List<TextRange> destination;
      if (detector == null || detector.getReferenceAccess(target, psiReference) == ReadWriteAccessDetector.Access.Read) {
        destination = readRanges;
      }
      else {
        destination = writeRanges;
      }
      HighlightUsagesHandler.collectRangesToHighlight(psiReference, destination);
    }

    if (withDeclarations) {
      final TextRange declRange = HighlightUsagesHandler.getNameIdentifierRange(psiElement.getContainingFile(), target);
      if (declRange != null) {
        if (detector != null && detector.isDeclarationWriteAccess(target)) {
          writeRanges.add(declRange);
        }
        else {
          readRanges.add(declRange);
        }
      }
    }

    return Couple.<Collection<TextRange>>of(readRanges, writeRanges);
  }

  private void highlightTargetUsages(@NotNull PsiElement target) {
    final Couple<Collection<TextRange>> usages = getHighlightUsages(target, myFile, true);
    myReadAccessRanges.addAll(usages.first);
    myWriteAccessRanges.addAll(usages.second);
  }

  @Override
  public void doApplyInformationToEditor() {
    final boolean virtSpace = TargetElementUtil.inVirtualSpace(myEditor, myEditor.getCaretModel().getOffset());
    final List<HighlightInfo> infos = virtSpace ? Collections.<HighlightInfo>emptyList() : getHighlights();
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
  }

  private List<HighlightInfo> getHighlights() {
    if (myReadAccessRanges.isEmpty() && myWriteAccessRanges.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Pair<Object, TextRange>> existingMarkupTooltips = new HashSet<>();
    for (RangeHighlighter highlighter : myEditor.getMarkupModel().getAllHighlighters()) {
      existingMarkupTooltips.add(Pair.create(highlighter.getErrorStripeTooltip(), new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset())));
    }

    List<HighlightInfo> result = new ArrayList<>(myReadAccessRanges.size() + myWriteAccessRanges.size());
    for (TextRange range: myReadAccessRanges) {
      ContainerUtil.addIfNotNull(result, createHighlightInfo(range, HighlightInfoType.ELEMENT_UNDER_CARET_READ, existingMarkupTooltips));
    }
    for (TextRange range: myWriteAccessRanges) {
      ContainerUtil.addIfNotNull(result, createHighlightInfo(range, HighlightInfoType.ELEMENT_UNDER_CARET_WRITE, existingMarkupTooltips));
    }
    return result;
  }

  private HighlightInfo createHighlightInfo(TextRange range, HighlightInfoType type, Set<Pair<Object, TextRange>> existingMarkupTooltips) {
    int start = range.getStartOffset();
    String tooltip = start <= myDocument.getTextLength() ? HighlightHandlerBase.getLineTextErrorStripeTooltip(myDocument, start, false) : null;
    String unescapedTooltip = existingMarkupTooltips.contains(new Pair<Object, TextRange>(tooltip, range)) ? null : tooltip;
    HighlightInfo.Builder builder = HighlightInfo.newHighlightInfo(type).range(range);
    if (unescapedTooltip != null) {
      builder.unescapedToolTip(unescapedTooltip);
    }
    return builder.createUnconditionally();
  }

  public static void clearMyHighlights(Document document, Project project) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      Object tooltip = highlighter.getErrorStripeTooltip();
      if (!(tooltip instanceof HighlightInfo)) {
        continue;
      }
      HighlightInfo info = (HighlightInfo)tooltip;
      if (info.type == HighlightInfoType.ELEMENT_UNDER_CARET_READ || info.type == HighlightInfoType.ELEMENT_UNDER_CARET_WRITE) {
        highlighter.dispose();
      }
    }
  }
}
