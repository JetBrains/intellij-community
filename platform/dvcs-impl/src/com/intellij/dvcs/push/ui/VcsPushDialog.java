// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class VcsPushDialog extends DialogWrapper implements VcsPushUi, DataProvider {

  private static final String ID = "Vcs.Push.Dialog";

  protected final Project myProject;
  private final PushLog myListPanel;
  protected final PushController myController;
  private final Map<PushSupport, VcsPushOptionsPanel> myAdditionalPanels;

  private Action myPushAction;
  @NotNull private final List<ActionWrapper> myAdditionalActions;

  public VcsPushDialog(@NotNull Project project,
                       @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo) {
    super(project, true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myProject = project;
    myController = new PushController(project, this, selectedRepositories, currentRepo);
    myAdditionalPanels = myController.createAdditionalPanels();
    myListPanel = myController.getPushPanelLog();

    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("Vcs.Push.Actions");
    myAdditionalActions = StreamEx.
      of(group.getChildren(null)).
      select(PushActionBase.class).
      map(action -> new ActionWrapper(myProject, this, action)).toList();

    init();
    updateOkActions();
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Push Commits");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel optionsPanel = createOptionsPanel();
    return JBUI.Panels.simplePanel(0, 2)
      .addToCenter(myListPanel)
      .addToBottom(optionsPanel);
  }

  @NotNull
  protected JPanel createOptionsPanel() {
    JPanel optionsPanel = new JPanel(new MigLayout("ins 0 0, flowx"));
    for (VcsPushOptionsPanel panel : myAdditionalPanels.values()) {
      optionsPanel.add(panel);
    }
    optionsPanel.setBorder(JBUI.Borders.emptyTop(6));
    return optionsPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return ID;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    updateOkActions();
    return null;
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  @Override
  protected void doOKAction() {
    push(false);
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    final List<Action> actions = new ArrayList<>();
    myPushAction = new ComplexPushAction(myAdditionalActions);
    myPushAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions.add(myPushAction);
    actions.add(getCancelAction());
    actions.add(getHelpAction());
    return actions.toArray(new Action[0]);
  }

  @Override
  public boolean canPush() {
    return myController.isPushAllowed();
  }

  @Override
  @NotNull
  public Map<PushSupport, Collection<PushInfo>> getSelectedPushSpecs() {
    return myController.getSelectedPushSpecs();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myListPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  protected Action getOKAction() {
    return myPushAction;
  }

  @Override
  protected String getHelpId() {
    return ID;
  }

  @Override
  @CalledInAwt
  public void push(boolean forcePush) {
    executeAfterRunningPrePushHandlers(new Task.Backgroundable(myProject, "Pushing...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myController.push(forcePush);
      }
    });
  }

  @Override
  @CalledInAwt
  public void executeAfterRunningPrePushHandlers(@NotNull Task.Backgroundable activity) {
    PrePushHandler.Result result = runPrePushHandlersInModalTask();
    if (result == PrePushHandler.Result.OK) {
      activity.queue();
      close(OK_EXIT_CODE);
    }
    else if (result == PrePushHandler.Result.ABORT_AND_CLOSE) {
      doCancelAction();
    }
    else if (result == PrePushHandler.Result.ABORT) {
      // cancel push and leave the push dialog open
    }
  }

  @CalledInAwt
  public PrePushHandler.Result runPrePushHandlersInModalTask() {
    FileDocumentManager.getInstance().saveAllDocuments();
    AtomicReference<PrePushHandler.Result> result = new AtomicReference<>(PrePushHandler.Result.OK);
    new Task.Modal(myController.getProject(), "Checking Commits...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result.set(myController.executeHandlers(indicator));
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        if (error instanceof PushController.HandlerException) {
          PushController.HandlerException handlerException = (PushController.HandlerException)error;
          Throwable cause = handlerException.getCause();

          String failedHandler = handlerException.getFailedHandlerName();
          List<String> skippedHandlers = handlerException.getSkippedHandlers();

          String suggestionMessage;
          if (cause instanceof ProcessCanceledException) {
            suggestionMessage = failedHandler + " has been cancelled.\n";
          }
          else {
            super.onThrowable(cause);
            suggestionMessage = failedHandler + " has failed. See log for more details.\n";
          }

          if (skippedHandlers.isEmpty()) {
            suggestionMessage += "Would you like to push anyway or cancel the push completely?";
          }
          else {
            suggestionMessage += "Would you like to skip all remaining pre-push steps and push, or cancel the push completely?";
          }

          suggestToSkipOrPush(suggestionMessage);
        } else {
          super.onThrowable(error);
        }
      }

      @Override
      public void onCancel() {
        super.onCancel();
        suggestToSkipOrPush("Would you like to skip all pre-push steps and push, or cancel the push completely?");
      }

      private void suggestToSkipOrPush(@NotNull String message) {
        if (Messages.showOkCancelDialog(myProject,
                                        message,
                                        "Push",
                                        "&Push Anyway",
                                        "&Cancel",
                                        UIUtil.getWarningIcon()) == Messages.OK) {
          result.set(PrePushHandler.Result.OK);
        }
        else {
          result.set(PrePushHandler.Result.ABORT);
        }
      }
    }.queue();
    return result.get();
  }

  public void updateOkActions() {
    myPushAction.setEnabled(canPush());
    for (ActionWrapper wrapper : myAdditionalActions) {
      wrapper.update();
    }
  }

  public void enableOkActions(boolean value) {
    myPushAction.setEnabled(value);
  }

  @Override
  @Nullable
  public VcsPushOptionValue getAdditionalOptionValue(@NotNull PushSupport support) {
    VcsPushOptionsPanel panel = myAdditionalPanels.get(support);
    return panel == null ? null : panel.getValue();
  }

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    if (VcsPushUi.VCS_PUSH_DIALOG.is(dataId)) {
      return this;
    }
    return null;
  }

  private class ComplexPushAction extends AbstractAction implements OptionAction {
    private final List<ActionWrapper> myOptions;

    private ComplexPushAction(@NotNull List<ActionWrapper> additionalActions) {
      super("&Push");
      myOptions = additionalActions;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      push(false);
    }

    @Override
    public void setEnabled(boolean isEnabled) {
      super.setEnabled(isEnabled);
      for (Action optionAction : myOptions) {
        optionAction.setEnabled(isEnabled);
      }
    }

    @NotNull
    @Override
    public Action[] getOptions() {
      return ArrayUtil.toObjectArray(myAdditionalActions, ActionWrapper.class);
    }
  }

  private static class ActionWrapper extends AbstractAction {

    @NotNull private final Project myProject;
    @NotNull private final VcsPushUi myDialog;
    @NotNull private final PushActionBase myRealAction;

    ActionWrapper(@NotNull Project project, @NotNull VcsPushUi dialog, @NotNull PushActionBase realAction) {
      super(realAction.getTemplatePresentation().getTextWithMnemonic());
      myProject = project;
      myDialog = dialog;
      myRealAction = realAction;
      putValue(OptionAction.AN_ACTION, realAction);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myRealAction.actionPerformed(myProject, myDialog);
    }

    public void update() {
      boolean enabled = myRealAction.isEnabled(myDialog);
      setEnabled(enabled);
      putValue(Action.SHORT_DESCRIPTION, myRealAction.getDescription(myDialog, enabled));
    }
  }
}
