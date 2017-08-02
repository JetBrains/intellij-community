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
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.codeInsight.hints.ParameterHintsPassFactory.forceHintsUpdateOnNextPass;
import static com.intellij.codeInsight.hints.ParameterHintsPassFactory.putCurrentPsiModificationStamp;

public class ParameterHintsPass extends EditorBoundHighlightingPass {
  private final TIntObjectHashMap<List<HintData>> myHints = new TIntObjectHashMap<>();
  private final TIntObjectHashMap<String> myShowOnlyIfExistedBeforeHints = new TIntObjectHashMap<>();
  private final SyntaxTraverser<PsiElement> myTraverser;
  private final PsiElement myRootElement;
  private final HintInfoFilter myHintInfoFilter;
  private final boolean myForceImmediateUpdate;

  public static void syncUpdate(@NotNull PsiElement element, @NotNull Editor editor) {
    MethodInfoBlacklistFilter filter = MethodInfoBlacklistFilter.forLanguage(element.getLanguage());
    ParameterHintsPass pass = new ParameterHintsPass(element, editor, filter, true);
    pass.doCollectInformation(new ProgressIndicatorBase());
    pass.applyInformationToEditor();
  }

  public ParameterHintsPass(@NotNull PsiElement element,
                            @NotNull Editor editor,
                            @NotNull HintInfoFilter hintsFilter,
                            boolean forceImmediateUpdate) {
    super(editor, element.getContainingFile(), true);
    myRootElement = element;
    myTraverser = SyntaxTraverser.psiTraverser(element);
    myHintInfoFilter = hintsFilter;
    myForceImmediateUpdate = forceImmediateUpdate;
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    assert myDocument != null;
    myHints.clear();

    Language language = myFile.getLanguage();
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null || !provider.canShowHintsWhenDisabled() && !isEnabled()) return;

    myTraverser.forEach(element -> process(element, provider));
  }

  private static boolean isEnabled() {
    return EditorSettingsExternalizable.getInstance().isShowParameterNameHints();
  }

  private void process(PsiElement element, InlayParameterHintsProvider provider) {
    List<InlayInfo> hints = provider.getParameterHints(element);
    if (hints.isEmpty()) return;
    HintInfo info = provider.getHintInfo(element);

    boolean showHints = info == null || info instanceof HintInfo.OptionInfo || myHintInfoFilter.showHint(info);

    Stream<InlayInfo> inlays = hints.stream();
    if (!showHints) {
      inlays = inlays.filter((inlayInfo -> !inlayInfo.isFilterByBlacklist()));
    }

    inlays.forEach((hint) -> {
      int offset = hint.getOffset();
      TextRange rootRange = myRootElement.getTextRange();
      if (offset <= rootRange.getStartOffset() || offset >= rootRange.getEndOffset()) return;
      String presentation = provider.getInlayPresentation(hint.getText());
      if (hint.isShowOnlyIfExistedBefore()) {
        myShowOnlyIfExistedBeforeHints.put(offset, presentation);
      }
      else {
        List<HintData> hintList = myHints.get(offset);
        if (hintList == null) myHints.put(offset, hintList = new ArrayList<>());
        hintList.add(new HintData(presentation, hint.getShowAfterCaret()));
      }
    });
  }

  @Override
  public void doApplyInformationToEditor() {
    CaretVisualPositionKeeper keeper = new CaretVisualPositionKeeper(myEditor);
    ParameterHintsPresentationManager manager = ParameterHintsPresentationManager.getInstance();
    List<Inlay> hints = hintsInRootElementArea(manager);
    ParameterHintsUpdater updater = new ParameterHintsUpdater(myEditor, hints, myHints, myShowOnlyIfExistedBeforeHints, myForceImmediateUpdate);
    updater.update();
    keeper.restoreOriginalLocation(false);

    if (ParameterHintsUpdater.hintRemovalDelayed(myEditor)) {
      forceHintsUpdateOnNextPass(myEditor);
    }
    else {
      putCurrentPsiModificationStamp(myEditor, myFile);
    }
  }

  @NotNull
  private List<Inlay> hintsInRootElementArea(ParameterHintsPresentationManager manager) {
    assert myDocument != null;

    TextRange range = myRootElement.getTextRange();
    int elementStart = range.getStartOffset();
    int elementEnd = range.getEndOffset();

    List<Inlay> inlays = myEditor.getInlayModel()
      .getInlineElementsInRange(elementStart + 1, elementEnd - 1);

    return ContainerUtil.filter(inlays, (hint) -> manager.isParameterHint(hint));
  }
  
  public static class HintData {
    public final String presentationText;
    public final boolean showAfterCaret;

    public HintData(String text, boolean afterCaret) {
      presentationText = text;
      showAfterCaret = afterCaret;
    }
  }
}