// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints;

import com.intellij.codeHighlighting.EditorBoundHighlightingPass;
import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager;
import com.intellij.codeWithMe.ClientId;
import com.intellij.lang.Language;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.concurrency.AppExecutorUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.intellij.codeInsight.hints.ParameterHintsPassFactory.forceHintsUpdateOnNextPass;
import static com.intellij.codeInsight.hints.ParameterHintsPassFactory.putCurrentPsiModificationStamp;

// TODO This pass should be rewritten with new API
public final class ParameterHintsPass extends EditorBoundHighlightingPass {
  private final Int2ObjectMap<List<HintData>> myHints = new Int2ObjectOpenHashMap<>();
  private final Int2ObjectMap<String> myShowOnlyIfExistedBeforeHints = new Int2ObjectOpenHashMap<>();
  private final PsiElement myRootElement;
  private final HintInfoFilter myHintInfoFilter;
  private final boolean myForceImmediateUpdate;

  public ParameterHintsPass(@NotNull PsiElement element,
                            @NotNull Editor editor,
                            @NotNull HintInfoFilter hintsFilter,
                            boolean forceImmediateUpdate) {
    super(editor, element.getContainingFile(), true);
    myRootElement = element;
    myHintInfoFilter = hintsFilter;
    myForceImmediateUpdate = forceImmediateUpdate;
  }

  /**
   * Updates inlays recursively for a given element.
   * Use {@link NonBlockingReadActionImpl#waitForAsyncTaskCompletion() } in tests to wait for the results.
   * <p>
   * Return promise in EDT.
   */
  public static @NotNull CancellablePromise<?> asyncUpdate(@NotNull PsiElement element, @NotNull Editor editor) {
    MethodInfoExcludeListFilter filter = MethodInfoExcludeListFilter.forLanguage(element.getLanguage());
    AsyncPromise<Object> promise = new AsyncPromise<>();
    SmartPsiElementPointer<PsiElement> elementPtr = SmartPointerManager.getInstance(element.getProject())
      .createSmartPsiElementPointer(element);
    ReadAction.nonBlocking(() -> collectInlaysInPass(editor, filter, elementPtr))
      .finishOnUiThread(ModalityState.any(), pass -> {
        if (pass != null) {
          try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.getClientId(editor))) {
            pass.applyInformationToEditor();
          }
        }
        promise.setResult(null);
      })
      .submit(AppExecutorUtil.getAppExecutorService());
    return promise;
  }

  private static ParameterHintsPass collectInlaysInPass(@NotNull Editor editor,
                                                        MethodInfoExcludeListFilter filter,
                                                        SmartPsiElementPointer<PsiElement> elementPtr) {
    PsiElement element = elementPtr.getElement();
    if (element == null || editor.isDisposed()) return null;
    try (AccessToken ignored = ClientId.withClientId(ClientEditorManager.getClientId(editor))) {
      ParameterHintsPass pass = new ParameterHintsPass(element, editor, filter, true);
      pass.doCollectInformation(new ProgressIndicatorBase());
      return pass;
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }

  @Override
  public void doCollectInformation(@NotNull ProgressIndicator progress) {
    myHints.clear();

    Language language = myFile.getLanguage();
    InlayParameterHintsProvider provider = InlayParameterHintsExtension.INSTANCE.forLanguage(language);
    if (provider == null || !provider.canShowHintsWhenDisabled() && !isEnabled(language) || DiffUtil.isDiffEditor(myEditor)) return;
    if (!HighlightingLevelManager.getInstance(myFile.getProject()).shouldHighlight(myFile)) return;

    provider.createTraversal(myRootElement).forEach(element -> process(element, provider));
  }

  private static boolean isEnabled(Language language) {
    return HintUtilsKt.isParameterHintsEnabledForLanguage(language);
  }

  private void process(@NotNull PsiElement element, @NotNull InlayParameterHintsProvider provider) {
    List<InlayInfo> hints = provider.getParameterHints(element, myFile);
    if (hints.isEmpty()) return;
    HintInfo info = provider.getHintInfo(element, myFile);

    boolean showHints = info == null || info instanceof HintInfo.OptionInfo || myHintInfoFilter.showHint(info);

    Stream<InlayInfo> inlays = hints.stream();
    if (!showHints) {
      inlays = inlays.filter(inlayInfo -> !inlayInfo.isFilterByExcludeList());
    }

    inlays.forEach(hint -> {
      int offset = hint.getOffset();
      if (!canShowHintsAtOffset(offset)) return;
      if (ParameterNameHintsSuppressor.All.isSuppressedFor(myFile, hint)) return;

      String presentation = provider.getInlayPresentation(hint.getText());
      if (hint.isShowOnlyIfExistedBefore()) {
        myShowOnlyIfExistedBeforeHints.put(offset, presentation);
      }
      else {
        List<HintData> hintList = myHints.get(offset);
        if (hintList == null) myHints.put(offset, hintList = new ArrayList<>());
        HintWidthAdjustment widthAdjustment = convertHintPresentation(hint.getWidthAdjustment(), provider);
        hintList.add(new HintData(presentation, hint.getRelatesToPrecedingText(), widthAdjustment));
      }
    });
  }

  private static HintWidthAdjustment convertHintPresentation(HintWidthAdjustment widthAdjustment,
                                                             InlayParameterHintsProvider provider) {
    if (widthAdjustment != null) {
      String hintText = widthAdjustment.getHintTextToMatch();
      if (hintText != null) {
        String adjusterHintPresentation = provider.getInlayPresentation(hintText);
        if (!hintText.equals(adjusterHintPresentation)) {
          widthAdjustment = new HintWidthAdjustment(widthAdjustment.getEditorTextToMatch(),
                                                    adjusterHintPresentation,
                                                    widthAdjustment.getAdjustmentPosition());
        }
      }
    }
    return widthAdjustment;
  }

  @Override
  public void doApplyInformationToEditor() {
    EditorScrollingPositionKeeper.perform(myEditor, false, () -> {
      ParameterHintsPresentationManager manager = ParameterHintsPresentationManager.getInstance();
      List<Inlay<?>> hints = hintsInRootElementArea(manager);
      ParameterHintsUpdater updater = new ParameterHintsUpdater(myEditor, hints, myHints, myShowOnlyIfExistedBeforeHints, myForceImmediateUpdate);
      updater.update();
    });

    if (ParameterHintsUpdater.hintRemovalDelayed(myEditor)) {
      forceHintsUpdateOnNextPass(myEditor);
    }
    else if (myRootElement == myFile) {
      putCurrentPsiModificationStamp(myEditor, myFile);
    }
  }

  private @NotNull List<Inlay<?>> hintsInRootElementArea(ParameterHintsPresentationManager manager) {
    TextRange range = myRootElement.getTextRange();
    int elementStart = range.getStartOffset();
    int elementEnd = range.getEndOffset();

    // Adding hints on the borders is allowed only in case root element is a document
    // See: canShowHintsAtOffset
    if (myDocument.getTextLength() != range.getLength()) {
      ++elementStart;
      --elementEnd;
    }

    return manager.getParameterHintsInRange(myEditor, elementStart, elementEnd);
  }

  /**
   * Adding hints on the borders of root element (at startOffset or endOffset)
   * is allowed only in the case when root element is a document
   *
   * @return true if a given offset can be used for hint rendering
   */
  private boolean canShowHintsAtOffset(int offset) {
    TextRange rootRange = myRootElement.getTextRange();

    if (!rootRange.containsOffset(offset)) return false;
    if (offset > rootRange.getStartOffset() && offset < rootRange.getEndOffset()) return true;

    return myDocument.getTextLength() == rootRange.getLength();
  }

  static final class HintData {
    final String presentationText;
    final boolean relatesToPrecedingText;
    final HintWidthAdjustment widthAdjustment;

    HintData(@NotNull String text, boolean relatesToPrecedingText, HintWidthAdjustment widthAdjustment) {
      presentationText = text;
      this.relatesToPrecedingText = relatesToPrecedingText;
      this.widthAdjustment = widthAdjustment;
    }

    @Override
    public @NotNull String toString() {
      return '\'' + presentationText + '\'';
    }
  }
}