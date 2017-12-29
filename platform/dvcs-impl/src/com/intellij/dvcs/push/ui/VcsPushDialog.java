/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class VcsPushDialog extends DialogWrapper {

  private static final String ID = "Vcs.Push.Dialog";

  private final PushLog myListPanel;
  private final PushController myController;
  private final Map<PushSupport, VcsPushOptionsPanel> myAdditionalPanels;

  private Action myPushAction;
  @Nullable private ForcePushAction myForcePushAction;

  public VcsPushDialog(@NotNull Project project,
                       @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo) {
    super(project, true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myController = new PushController(project, this, selectedRepositories, currentRepo);
    myAdditionalPanels = myController.createAdditionalPanels();
    myListPanel = myController.getPushPanelLog();

    init();
    updateOkActions();
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Push Commits");
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel(new MigLayout("ins 0 0, flowx"));
    for (VcsPushOptionsPanel panel : myAdditionalPanels.values()) {
      optionsPanel.add(panel);
    }
    optionsPanel.setBorder(JBUI.Borders.emptyTop(6));
    return JBUI.Panels.simplePanel(0, 2)
      .addToCenter(myListPanel)
      .addToBottom(optionsPanel);
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
    myForcePushAction = new ForcePushAction();
    myForcePushAction.setEnabled(canForcePush());
    myForcePushAction.putValue(Action.NAME, "&Force Push");
    myPushAction = new ComplexPushAction(myForcePushAction);
    myPushAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions.add(myPushAction);
    actions.add(getCancelAction());
    actions.add(getHelpAction());
    return actions.toArray(new Action[actions.size()]);
  }

  private boolean canPush() {
    return myController.isPushAllowed();
  }

  private boolean canForcePush() {
    return myController.getProhibitedTarget() == null && myController.isPushAllowed();
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

  @CalledInAwt
  private void push(boolean forcePush) {
    FileDocumentManager.getInstance().saveAllDocuments();
    AtomicReference<PrePushHandler.Result> result = new AtomicReference<>(PrePushHandler.Result.OK);
    new Task.Modal(myController.getProject(), "Checking Commits...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        result.set(myController.executeHandlers(indicator));
      }

      @Override
      public void onSuccess() {
        super.onSuccess();
        if (result.get() == PrePushHandler.Result.OK) {
          doPush();
        }
        else if (result.get() == PrePushHandler.Result.ABORT_AND_CLOSE) {
          doCancelAction();
        }
        else if (result.get() == PrePushHandler.Result.ABORT) {
          // cancel push and leave the push dialog open
        }
      }

      private void doPush() {
        myController.push(forcePush);
        close(OK_EXIT_CODE);
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
          doPush();
        }
      }
    }.queue();
  }

  public void updateOkActions() {
    myPushAction.setEnabled(canPush());
    if (myForcePushAction != null) {
      boolean canForcePush = canForcePush();
      myForcePushAction.setEnabled(canForcePush);
      String tooltip = null;
      PushTarget target = myController.getProhibitedTarget();
      if (!canForcePush && target != null) {
        tooltip = "Force push to <b>" + target.getPresentation() + "</b> is prohibited";
      }
      myForcePushAction.putValue(Action.SHORT_DESCRIPTION, tooltip);
    }
  }

  public void enableOkActions(boolean value) {
    myPushAction.setEnabled(value);
  }

  @Nullable
  public VcsPushOptionValue getAdditionalOptionValue(@NotNull PushSupport support) {
    VcsPushOptionsPanel panel = myAdditionalPanels.get(support);
    return panel == null ? null : panel.getValue();
  }

  private class ForcePushAction extends AbstractAction {
    ForcePushAction() {
      super("&Force Push");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myController.ensureForcePushIsNeeded()) {
        push(true);
      }
    }
  }

  private class ComplexPushAction extends AbstractAction implements OptionAction {
    private final Action[] myOptions;

    private ComplexPushAction(Action additionalAction) {
      super("&Push");
      myOptions = new Action[]{additionalAction};
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
      return myOptions;
    }
  }
}
