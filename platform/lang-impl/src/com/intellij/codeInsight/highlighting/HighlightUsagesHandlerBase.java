// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public abstract class HighlightUsagesHandlerBase<T extends PsiElement> implements PossiblyDumbAware {
  protected final @NotNull Editor myEditor;
  protected final @NotNull PsiFile myFile;

  protected final List<TextRange> myReadUsages = new ArrayList<>();
  protected final List<TextRange> myWriteUsages = new ArrayList<>();
  protected @NlsContexts.StatusBarText String myStatusText;
  protected @NlsContexts.HintText String myHintText;

  protected HighlightUsagesHandlerBase(@NotNull Editor editor, @NotNull PsiFile file) {
    myEditor = editor;
    myFile = file;
  }

  public void highlightUsages() {
    List<T> targets = getTargets();
    selectTargets(targets, targets1 -> {
      computeUsages(targets1);
      performHighlighting();
    });
  }

  private void performHighlighting() {
    boolean clearHighlights = HighlightUsagesHandler.isClearHighlights(myEditor);
    HighlightUsagesHandler.highlightRanges(HighlightManager.getInstance(myFile.getProject()),
                                           myEditor, EditorColors.SEARCH_RESULT_ATTRIBUTES, clearHighlights, myReadUsages);
    HighlightUsagesHandler.highlightRanges(HighlightManager.getInstance(myFile.getProject()),
                                           myEditor, EditorColors.WRITE_SEARCH_RESULT_ATTRIBUTES, clearHighlights, myWriteUsages);
    if (!clearHighlights) {
      WindowManager.getInstance().getStatusBar(myFile.getProject()).setInfo(myStatusText);

      HighlightHandlerBase.setupFindModel(myFile.getProject()); // enable f3 navigation
    }
    if (myHintText != null) {
      HintManager.getInstance().showInformationHint(myEditor, myHintText);
    }
  }

  protected void buildStatusText(@Nullable String elementName, int refCount) {
    if (refCount > 0) {
      myStatusText = CodeInsightBundle.message(elementName != null ?
                                        "status.bar.highlighted.usages.message" :
                                        "status.bar.highlighted.usages.no.target.message", refCount, elementName,
                                               HighlightUsagesHandler.getShortcutText());
    }
    else {
      myHintText = CodeInsightBundle.message(elementName != null ?
                                          "status.bar.highlighted.usages.not.found.message" :
                                          "status.bar.highlighted.usages.not.found.no.target.message", elementName);
    }
  }

  public abstract @NotNull List<T> getTargets();

  public @Nullable String getFeatureId() {
    return null;
  }

  protected abstract void selectTargets(@NotNull List<? extends T> targets, @NotNull Consumer<? super List<? extends T>> selectionConsumer);

  public abstract void computeUsages(@NotNull List<? extends T> targets);

  protected void addOccurrence(@NotNull PsiElement element) {
    TextRange range = element.getTextRange();
    if (range != null) {
      range = InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, range);
      myReadUsages.add(range);
    }
  }

  public List<TextRange> getReadUsages() {
    return myReadUsages;
  }

  public List<TextRange> getWriteUsages() {
    return myWriteUsages;
  }

  /**
   * In case of egoistic handler (highlightReferences = false) IdentifierHighlighterPass applies information only from this particular handler.
   * Otherwise additional information would be collected from reference search as well.
   */
  public boolean highlightReferences() {
    return false;
  }
}
