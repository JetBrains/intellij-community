// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.AbstractEmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IntentionListStep implements ListPopupStep<IntentionActionWithTextCaching>, SpeedSearchFilter<IntentionActionWithTextCaching> {
  private static final Logger LOG = Logger.getInstance(IntentionListStep.class);

  private final @NotNull CachedIntentions myCachedIntentions;
  private final @Nullable IntentionHintComponent.IntentionPopup myPopup;
  private final Dimension myMaxIconSize;

  private Runnable myFinalRunnable;
  private final Project myProject;
  private final PsiFile myFile;
  private final @Nullable Editor myEditor;

  public IntentionListStep(@Nullable IntentionHintComponent.IntentionPopup popup,
                           @Nullable Editor editor,
                           @NotNull PsiFile file,
                           @NotNull Project project,
                           @NotNull CachedIntentions intentions) {
    myPopup = popup;
    myProject = project;
    myFile = file;
    myEditor = editor;
    myCachedIntentions = intentions;
    myMaxIconSize = getMaxIconSize();
  }

  @Override
  public String getTitle() {
    return null;
  }

  @Override
  public boolean isSelectable(@NotNull IntentionActionWithTextCaching action) {
    return action.isSelectable();
  }

  @Override
  public PopupStep<?> onChosen(IntentionActionWithTextCaching action, boolean finalChoice) {
    IntentionAction a = IntentionActionDelegate.unwrap(action.getAction());

    if (finalChoice && !(a instanceof AbstractEmptyIntentionAction)) {
      applyAction(action);
      return FINAL_CHOICE;
    }

    if (hasSubstep(action)) {
      closeIntentionPreviewPopup();

      return getSubStep(action, action.getToolName());
    }

    return FINAL_CHOICE;
  }

  @Override
  public boolean isFinal(IntentionActionWithTextCaching value) {
    IntentionAction a = IntentionActionDelegate.unwrap(value.getAction());

    return  !(a instanceof AbstractEmptyIntentionAction) || !hasSubstep(value);
  }

  private void closeIntentionPreviewPopup() {
    if (myPopup != null) {
      myPopup.hidePreview();
    }
  }

  @Override
  public Runnable getFinalRunnable() {
    return myFinalRunnable;
  }

  private void applyAction(@NotNull IntentionActionWithTextCaching cachedAction) {
    myFinalRunnable = () -> {
      HintManager.getInstance().hideAllHints();
      if (myProject.isDisposed()) return;
      if (myEditor != null && (myEditor.isDisposed() || (!UIUtil.isShowing(myEditor.getComponent()) && !ApplicationManager.getApplication().isUnitTestMode()))) return;

      if (DumbService.isDumb(myProject) && !DumbService.isDumbAware(cachedAction)) {
        DumbService.getInstance(myProject).showDumbModeNotification(
          CodeInsightBundle.message("notification.0.is.not.available.during.indexing", cachedAction.getText()));
        return;
      }

      PsiDocumentManager.getInstance(myProject).commitAllDocuments();

      PsiFile file = myEditor != null ? PsiUtilBase.getPsiFileInEditor(myEditor, myProject) : myFile;
      if (file == null) {
        return;
      }

      chooseActionAndInvoke(cachedAction, file, myProject, myEditor);
    };
  }

  protected void chooseActionAndInvoke(@NotNull IntentionActionWithTextCaching cachedAction,
                                       @NotNull PsiFile file,
                                       @NotNull Project project,
                                       @Nullable Editor editor) {
    ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, cachedAction.getAction(), cachedAction.getText());
  }

  @NotNull
  IntentionListStep getSubStep(@NotNull IntentionActionWithTextCaching action, @NlsContexts.PopupTitle String title) {
    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    for (IntentionAction optionIntention : action.getOptionIntentions()) {
      intentions.intentionsToShow.add(new HighlightInfo.IntentionActionDescriptor(optionIntention, null, null, getIcon(optionIntention), null, null, null));
    }
    for (IntentionAction optionFix : action.getOptionErrorFixes()) {
      intentions.errorFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null, null, getIcon(optionFix), null, null, null));
    }
    for (IntentionAction optionFix : action.getOptionInspectionFixes()) {
      intentions.inspectionFixesToShow.add(new HighlightInfo.IntentionActionDescriptor(optionFix, null, null, getIcon(optionFix), null, null, null));
    }

    return new IntentionListStep(myPopup, myEditor, myFile, myProject,
                                 CachedIntentions.create(myProject, myFile, myEditor, intentions)){
      @Override
      public String getTitle() {
        return title;
      }

      @Override
      protected void chooseActionAndInvoke(@NotNull IntentionActionWithTextCaching cachedAction,
                                           @NotNull PsiFile file,
                                           @NotNull Project project,
                                           @Nullable Editor editor) {
        IntentionListStep.this.chooseActionAndInvoke(cachedAction, file, project, editor);
      }
    };
  }

  private static Icon getIcon(IntentionAction optionIntention) {
    return optionIntention instanceof Iconable ? ((Iconable)optionIntention).getIcon(0) : null;
  }

  @TestOnly
  public Map<IntentionAction, List<IntentionAction>> getActionsWithSubActions() {
    Map<IntentionAction, List<IntentionAction>> result = new LinkedHashMap<>();

    for (IntentionActionWithTextCaching cached : getValues()) {
      IntentionAction action = cached.getAction();
      if (ShowIntentionActionsHandler.chooseFileForAction(myFile, myEditor, action) == null) {
        continue;
      }

      if (!cached.isShowSubmenu()) {
        result.put(action, Collections.emptyList());
        continue;
      }

      List<IntentionActionWithTextCaching> subActions = getSubStep(cached, cached.getToolName()).getValues();
      List<IntentionAction> options = subActions.stream()
          .map(IntentionActionWithTextCaching::getAction)
          .filter(option -> ShowIntentionActionsHandler.chooseFileForAction(myFile, myEditor, option) != null)
          .collect(Collectors.toList());
      result.put(action, options);
    }
    return result;
  }

  @Override
  public boolean hasSubstep(IntentionActionWithTextCaching action) {
    if (!action.isShowSubmenu()) return false;

    return action.getOptionIntentions().size() + action.getOptionErrorFixes().size() > 0;
  }

  @Override
  public @NotNull List<IntentionActionWithTextCaching> getValues() {
    return myCachedIntentions.getAllActions();
  }

  @Override
  public @NotNull String getTextFor(IntentionActionWithTextCaching action) {
    String text = action.getText();
    if (LOG.isDebugEnabled() && text.startsWith("<html>")) {
      LOG.info("IntentionAction.getText() returned HTML: action=" + action.getAction().getClass() + " text=" + text);
    }
    return text;
  }

  @Override
  public Icon getIconFor(IntentionActionWithTextCaching value) {
    Icon icon = getOriginalIconFor(value);
    if (icon == null || icon instanceof EmptyIcon) {
      icon = EmptyIcon.create(myMaxIconSize.width, myMaxIconSize.height);
    }
    return icon;
  }

  public @Nullable Icon getOriginalIconFor(@NotNull IntentionActionWithTextCaching value) {
    if (!value.isShowIcon()) return null;

    return myCachedIntentions.getIcon(value);
  }

  @Override
  public void canceled() {
    if (myPopup != null) {
      myPopup.cancelled(this);
    }
  }

  @Override
  public int getDefaultOptionIndex() { return 0; }
  @Override
  public ListSeparator getSeparatorAbove(IntentionActionWithTextCaching value) {
    List<IntentionActionWithTextCaching> values = getValues();
    int index = values.indexOf(value);
    if (index <= 0) return null;
    IntentionActionWithTextCaching prev = values.get(index - 1);

    if (myCachedIntentions.getGroup(value) != myCachedIntentions.getGroup(prev)) {
      return new ListSeparator();
    }
    return null;
  }
  @Override
  public boolean isMnemonicsNavigationEnabled() { return false; }
  @Override
  public MnemonicNavigationFilter<IntentionActionWithTextCaching> getMnemonicNavigationFilter() { return null; }
  @Override
  public boolean isSpeedSearchEnabled() { return true; }
  @Override
  public boolean isAutoSelectionEnabled() { return false; }
  @Override
  public SpeedSearchFilter<IntentionActionWithTextCaching> getSpeedSearchFilter() { return this; }

  //speed search filter
  @Override
  public String getIndexedString(IntentionActionWithTextCaching value) { return getTextFor(value);}


  private @NotNull Dimension getMaxIconSize() {
    int maxWidth = -1;
    int maxHeight = -1;
    for (IntentionActionWithTextCaching action : myCachedIntentions.getAllActions()) {
      if (!action.isShowIcon()) continue;
      Icon icon = myCachedIntentions.getIcon(action);
      if (icon == null || icon instanceof EmptyIcon) continue;
      maxWidth = Math.max(maxWidth, icon.getIconWidth());
      maxHeight = Math.max(maxHeight, icon.getIconHeight());
    }
    return new Dimension(maxWidth, maxHeight);
  }
}
