/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.hints;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.util.CaretVisualPositionKeeper;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.codeInsight.hints.ParameterHintsPassFactory.putCurrentPsiModificationStamp;

public class ParameterHintsPass extends EditorBoundHighlightingPass {
  private final TIntObjectHashMap<String> myHints = new TIntObjectHashMap<>();
  private final TIntObjectHashMap<String> myShowOnlyIfExistedBeforeHints = new TIntObjectHashMap<>();
  private final SyntaxTraverser<PsiElement> myTraverser;
  private final PsiElement myRootElement;
  private final HintInfoFilter myHintInfoMatcher;

  public ParameterHintsPass(@NotNull PsiElement element,
                            @NotNull Editor editor,
                            @NotNull HintInfoFilter hintsFilter) {
    super(editor, element.getContainingFile(), true);
    myRootElement = element;
    myTraverser = SyntaxTraverser.psiTraverser(element);
    myHintInfoMatcher = hintsFilter;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    assert myDocument != null;
    myHints.clear();
    if (!isEnabled()) return;

    Language language = myFile.getLanguage();
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null) return;

    myTraverser.forEach(element -> process(element, provider));
  }

  private static boolean isEnabled() {
    return EditorSettingsExternalizable.getInstance().isShowParameterNameHints();
  }

  private void process(PsiElement element, InlayParameterHintsProvider provider) {
    List<InlayInfo> hints = provider.getParameterHints(element);
    if (hints.isEmpty()) return;
    HintInfo info = provider.getHintInfo(element);
    if (info == null || myHintInfoMatcher.showHint(info)) {
      hints.forEach((hint) -> {
        String presentation = provider.getInlayPresentation(hint.getText());
        int offset = hint.getOffset();
        if (hint.isShowOnlyIfExistedBefore()) {
          myShowOnlyIfExistedBeforeHints.put(offset, presentation);
        }
        else {
          myHints.put(offset, presentation);
        }
      });
    }
  }

  @Override
  public void doApplyInformationToEditor() {
    CaretVisualPositionKeeper keeper = new CaretVisualPositionKeeper(myEditor);
    ParameterHintsPresentationManager manager = ParameterHintsPresentationManager.getInstance();
    List<Inlay> hints = hintsInRootElementArea(manager);
    ParameterHintsUpdater updater = new ParameterHintsUpdater(myEditor, hints, myHints, myShowOnlyIfExistedBeforeHints);
    updater.update();
    keeper.restoreOriginalLocation(false);
    putCurrentPsiModificationStamp(myEditor, myFile);
  }

  @NotNull
  private List<Inlay> hintsInRootElementArea(ParameterHintsPresentationManager manager) {
    assert myDocument != null;

    TextRange range = myRootElement.getTextRange();
    int elementStart = range.getStartOffset();
    int elementEnd = range.getEndOffset();

    List<Inlay> inlays = myEditor.getInlayModel()
      .getInlineElementsInRange(elementStart, elementEnd);

    return ContainerUtil.filter(inlays, (hint) -> manager.isParameterHint(hint));
  }
}