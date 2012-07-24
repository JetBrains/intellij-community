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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.highlighting.HighlightHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
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

  private static final HighlightInfoType ourReadHighlightInfoType = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES);
  private static final HighlightInfoType ourWriteHighlightInfoType = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, EditorColors.WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES);

  protected IdentifierHighlighterPass(final Project project, final PsiFile file, final Editor editor) {
    super(project, editor.getDocument(), false);
    myFile = file;
    myEditor = editor;
    myCaretOffset = myEditor.getCaretModel().getOffset();
  }

  @Override
  public void doCollectInformation(@NotNull final ProgressIndicator progress) {
    if (!CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET) {
      return;
    }

    final HighlightUsagesHandlerBase<PsiElement> handler = HighlightUsagesHandler.createCustomHandler(myEditor, myFile);
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
      return;
    }

    int flags = TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED;
    PsiElement myTarget = TargetElementUtilBase.getInstance().findTargetElement(myEditor, flags, myCaretOffset);
    
    if (myTarget == null) {
      if (!PsiDocumentManager.getInstance(myProject).isUncommited(myEditor.getDocument())) {
        // when document is committed, try to check injected stuff - it's fast
        Editor injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, myFile, myCaretOffset);
        if (injectedEditor != null) {
          myTarget = TargetElementUtilBase.getInstance().findTargetElement(injectedEditor, flags, injectedEditor.getCaretModel().getOffset()); 
        }
      }
    }
    
    if (myTarget != null) {
      final ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(myTarget);
      final PsiElement finalMyTarget = myTarget;
      ReferencesSearch.search(myTarget, new LocalSearchScope(myFile)).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(final PsiReference psiReference) {
          final List<TextRange> textRanges = HighlightUsagesHandler.getRangesToHighlight(psiReference);
          if (detector == null || detector.getReferenceAccess(finalMyTarget, psiReference) == ReadWriteAccessDetector.Access.Read) {
            myReadAccessRanges.addAll(textRanges);
          }
          else {
            myWriteAccessRanges.addAll(textRanges);
          }
          return true;
        }
      });

      final TextRange declRange = HighlightUsagesHandler.getNameIdentifierRange(myFile, myTarget);
      if (declRange != null) {
        if (detector != null && detector.isDeclarationWriteAccess(myTarget)) {
          myWriteAccessRanges.add(declRange);
        }
        else {
          myReadAccessRanges.add(declRange);
        }
      }
    }
  }

  @Override
  public void doApplyInformationToEditor() {
    final boolean virtSpace = TargetElementUtilBase.inVirtualSpace(myEditor, myEditor.getCaretModel().getOffset());
    final List<HighlightInfo> infos = virtSpace ? Collections.<HighlightInfo>emptyList() : getHighlights();
    UpdateHighlightersUtil.setHighlightersToEditor(myProject, myDocument, 0, myFile.getTextLength(), infos, getColorsScheme(), getId());
  }

  private List<HighlightInfo> getHighlights() {
    if (myReadAccessRanges.isEmpty() && myWriteAccessRanges.isEmpty()) {
      return Collections.emptyList();
    }
    Set<Pair<Object, TextRange>> existingMarkupTooltips = new HashSet<Pair<Object, TextRange>>();
    for (RangeHighlighter highlighter : myEditor.getMarkupModel().getAllHighlighters()) {
      existingMarkupTooltips.add(Pair.create(highlighter.getErrorStripeTooltip(), new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset())));
    }

    List<HighlightInfo> result = new ArrayList<HighlightInfo>(myReadAccessRanges.size() + myWriteAccessRanges.size());
    for (TextRange range: myReadAccessRanges) {
      ContainerUtil.addIfNotNull(createHighlightInfo(range, ourReadHighlightInfoType, existingMarkupTooltips), result);
    }
    for (TextRange range: myWriteAccessRanges) {
      ContainerUtil.addIfNotNull(createHighlightInfo(range, ourWriteHighlightInfoType, existingMarkupTooltips), result);
    }
    return result;
  }

  private HighlightInfo createHighlightInfo(TextRange range, HighlightInfoType type, Set<Pair<Object, TextRange>> existingMarkupTooltips) {
    String tooltip = HighlightHandlerBase.getLineTextErrorStripeTooltip(myDocument, range.getStartOffset());
    return HighlightInfo.createHighlightInfo(type, range, null, existingMarkupTooltips.contains(new Pair<Object, TextRange>(tooltip, range)) ? null : tooltip, null);
  }

  public static void clearMyHighlights(Document document, Project project) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(document, project, true);
    for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
      Object tooltip = highlighter.getErrorStripeTooltip();
      if (!(tooltip instanceof HighlightInfo)) {
        continue;
      }
      HighlightInfo info = (HighlightInfo)tooltip;
      if (info.type == ourReadHighlightInfoType || info.type == ourWriteHighlightInfoType) {
        highlighter.dispose();
      }
    }
  }
}
