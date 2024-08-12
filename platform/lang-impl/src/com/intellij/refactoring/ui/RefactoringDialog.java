// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.ui;

import com.intellij.ide.HelpTooltip;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.*;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.Button;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.Refactoring;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Author: msk
 */
public abstract class RefactoringDialog extends DialogWrapper implements PossiblyDumbAware {
  private Action myRefactorAction;
  private Action myPreviewAction;
  private boolean myCbPreviewResults;
  protected final Project myProject;

  protected RefactoringDialog(@NotNull Project project, boolean canBeParent) {
    this(project, canBeParent, false);
  }

  protected RefactoringDialog(@NotNull Project project, boolean canBeParent, boolean addOpenInEditorCheckbox) {
    super(project, canBeParent);
    myCbPreviewResults = true;
    myProject = project;
    if (addOpenInEditorCheckbox) {
      addOpenInEditorCheckbox();
    }
  }

  /**
   * Must be called before {@link #init()}.
   */
  protected void addOpenInEditorCheckbox() {
    setDoNotAskOption(new DoNotAskOption.Adapter() {
      @Override
      public void rememberChoice(boolean selected, int exitCode) {
        PropertiesComponent.getInstance().setValue(getRefactoringId() + ".OpenInEditor", selected, isOpenInEditorEnabledByDefault());
        RefactoringDialogUsageCollector.logOpenInEditorSaved(myProject, selected, RefactoringDialog.this.getClass());
      }

      @Override
      public boolean isSelectedByDefault() {
        boolean selected = PropertiesComponent.getInstance().getBoolean(getRefactoringId() + ".OpenInEditor", isOpenInEditorEnabledByDefault());
        RefactoringDialogUsageCollector.logOpenInEditorShown(myProject, selected, RefactoringDialog.this.getClass());
        return selected;
      }

      @Override
      public @NotNull String getDoNotShowMessage() {
        return RefactoringBundle.message("open.in.editor.label");
      }
    });
  }

  protected boolean isOpenInEditorEnabledByDefault() {
    return true;
  }

  protected @NonNls @NotNull String getRefactoringId() {
    return getClass().getName();
  }

  public boolean isOpenInEditor() {
    return myCheckBoxDoNotShowDialog != null && myCheckBoxDoNotShowDialog.isSelected();
  }

  public final boolean isPreviewUsages() {
    return myCbPreviewResults;
  }

  public void setPreviewResults(boolean previewResults) {
    myCbPreviewResults = previewResults;
  }

  @Override
  public void show() {
    IdeEventQueue.getInstance().getPopupManager().closeAllPopups(false);
    super.show();
  }

  @Override
  protected void createDefaultActions() {
    super.createDefaultActions();
    myRefactorAction = new RefactorAction();
    myPreviewAction = new PreviewAction();
  }

  /**
   * @return default implementation of "Refactor" action.
   */
  protected final Action getRefactorAction() {
    return myRefactorAction;
  }

  protected final void setRefactorButtonText(@Button @NotNull String text) {
    myRefactorAction.putValue(Action.NAME, text);
    myRefactorAction.putValue(Action.DISPLAYED_MNEMONIC_INDEX_KEY, null);
    myRefactorAction.putValue(Action.MNEMONIC_KEY, null);
  }

  /**
   * @return default implementation of "Preview" action.
   */
  protected final Action getPreviewAction() {
    return myPreviewAction;
  }

  protected abstract void doAction();

  private void doPreviewAction() {
    myCbPreviewResults = true;
    doAction();
  }

  protected void doRefactorAction() {
    myCbPreviewResults = false;
    doAction();
  }

  protected final void closeOKAction() { super.doOKAction(); }

  @Override
  protected final void doOKAction() {
    if (!DumbService.getInstance(myProject).isUsableInCurrentContext(this)) {
      Messages.showMessageDialog(myProject, RefactoringBundle.message("refactoring.not.available.indexing"),
                                 RefactoringBundle.message("refactoring.indexing.warning.title"), null);
      DumbModeBlockedFunctionalityCollector.INSTANCE.logFunctionalityBlocked(myProject, DumbModeBlockedFunctionality.RefactoringDialog);
      return;
    }

    doAction();
  }

  protected boolean areButtonsValid() { return true; }

  protected void canRun() throws ConfigurationException {
    if (!areButtonsValid()) throw new ConfigurationException(null);
  }

  @Override
  protected void setHelpTooltip(@NotNull JButton helpButton) {
    if (UISettings.isIdeHelpTooltipEnabled()) {
      new HelpTooltip().setDescription(ActionsBundle.actionDescription("HelpTopics")).installOn(helpButton);
    }
    else {
      super.setHelpTooltip(helpButton);
    }
  }

  protected void validateButtons() {
    boolean enabled = true;
    try {
      setErrorText(null);
      canRun();
    }
    catch (ConfigurationException e) {
      enabled = false;
      setErrorHtml(e.getMessageHtml());
    }
    getPreviewAction().setEnabled(enabled);
    getRefactorAction().setEnabled(enabled);
  }

  protected void validateButtonsAsync() {
    validateButtonsAsync(ModalityState.stateForComponent(getContentPanel()));
  }

  protected void validateButtonsAsync(@NotNull ModalityState modalityState) {
    ReadAction.nonBlocking(() -> {
        try {
          canRun();
          return null;
        }
        catch (ConfigurationException e) {
          return e;
        }
      }).finishOnUiThread(modalityState, e -> {
        setErrorHtml(e == null ? null : e.getMessageHtml());
        getPreviewAction().setEnabled(e == null);
        getRefactorAction().setEnabled(e == null);
      })
      .coalesceBy(myProject, getClass())
      .submit(AppExecutorUtil.getAppExecutorService());
  }

  protected boolean hasHelpAction() {
    return true;
  }

  protected boolean hasPreviewButton() {
    return true;
  }

  @Override
  protected Action @NotNull [] createActions() {
    List<Action> actions = new ArrayList<>();
    actions.add(getRefactorAction());
    if (hasPreviewButton()) {
      actions.add(getPreviewAction());
    }
    actions.add(getCancelAction());
    if (hasHelpAction()) {
      actions.add(getHelpAction());
    }
    if (SystemInfo.isMac) {
      Collections.reverse(actions);
    }
    return actions.toArray(new Action[0]);
  }

  protected Project getProject() {
    return myProject;
  }

  private final class RefactorAction extends AbstractAction {
    RefactorAction() {
      super(RefactoringBundle.message("refactor.button"));
      putValue(DEFAULT_ACTION, Boolean.TRUE);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        doRefactorAction();
      }
    }
  }

  private final class PreviewAction extends AbstractAction {
    PreviewAction() {
      super(RefactoringBundle.message("preview.button"));
      if (SystemInfo.isMac) {
        putValue(FOCUSED_ACTION, Boolean.TRUE);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        doPreviewAction();
      }
    }
  }

  protected void invokeRefactoring(BaseRefactoringProcessor processor) {
    final Runnable prepareSuccessfulCallback = () -> close(DialogWrapper.OK_EXIT_CODE);
    processor.setPrepareSuccessfulSwingThreadCallback(prepareSuccessfulCallback);
    processor.setPreviewUsages(isPreviewUsages());
    processor.run();
  }
  
  protected void invokeRefactoring(Refactoring processor) {
    final Runnable prepareSuccessfulCallback = () -> close(DialogWrapper.OK_EXIT_CODE);
    processor.setInteractive(prepareSuccessfulCallback);
    processor.setPreviewUsages(isPreviewUsages());
    processor.run();
  }
}
