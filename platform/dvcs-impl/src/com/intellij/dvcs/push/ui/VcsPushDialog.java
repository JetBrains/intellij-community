// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
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
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Objects.requireNonNull;

public class VcsPushDialog extends DialogWrapper implements VcsPushUi, DataProvider {
  @NonNls private static final String DIMENSION_KEY = "Vcs.Push.Dialog.v2";
  @NonNls private static final String HELP_ID = "Vcs.Push.Dialog";
  private static final Logger LOG = Logger.getInstance(VcsPushDialog.class);
  private static final ExtensionPointName<PushDialogCustomizer> PUSH_DIALOG_CUSTOMIZER_EP =
    ExtensionPointName.create("com.intellij.pushDialogCustomizer");
  private static final ExtensionPointName<PushDialogActionsProvider> PUSH_DIALOG_ACTIONS_PROVIDER_EP =
    ExtensionPointName.create("com.intellij.pushDialogActionsProvider");

  private static final int CENTER_PANEL_HEIGHT = 450;
  private static final int CENTER_PANEL_WIDTH = 800;

  protected final Project myProject;
  private final PushLog myListPanel;
  protected final PushController myController;
  private final Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> myAdditionalPanels;

  private Action myMainAction;
  @NotNull private final List<ActionWrapper> myPushActions;

  public VcsPushDialog(@NotNull Project project,
                       @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo) {
    this(project, VcsRepositoryManager.getInstance(project).getRepositories(), selectedRepositories, currentRepo, null);
  }

  public VcsPushDialog(@NotNull Project project,
                       Collection<? extends Repository> allRepos, @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo, @Nullable PushSource pushSource) {
    super(project, true, (Registry.is("ide.perProjectModality")) ? IdeModalityType.PROJECT : IdeModalityType.IDE);
    myProject = project;
    myController =
      new PushController(project, this, allRepos, selectedRepositories, currentRepo,
                         pushSource);
    myAdditionalPanels = myController.createAdditionalPanels();
    myListPanel = myController.getPushPanelLog();
    myPushActions = collectPushActions();

    init();
    updateOkActions();
    setOKButtonText(DvcsBundle.message("action.push"));
    String title = allRepos.size() == 1
                   ? DvcsBundle.message("push.dialog.push.commits.to.title", DvcsUtil.getShortRepositoryName(getFirstItem(allRepos)))
                   : DvcsBundle.message("push.dialog.push.commits.title");
    setTitle(title);
  }

  private @NotNull List<ActionWrapper> collectPushActions() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("Vcs.Push.Actions");
    List<PushActionBase> pushActions = ContainerUtil.findAll(group.getChildren(null), PushActionBase.class);

    customizeDialog(ContainerUtil.findInstance(pushActions, SimplePushAction.class));

    List<PushDialogActionsProvider> actionProviders = PUSH_DIALOG_ACTIONS_PROVIDER_EP.getExtensionList();
    for (PushDialogActionsProvider actionProvider : ContainerUtil.reverse(actionProviders)) {
      pushActions.addAll(0, actionProvider.getCustomActionsAboveDefault(myProject));
    }
    int firstEnabledActionPosition = ContainerUtil.indexOf(pushActions, it -> it.isEnabled(this));
    if (firstEnabledActionPosition >= 0) {
      PushActionBase firstEnabledAction = pushActions.remove(firstEnabledActionPosition);
      pushActions.add(0, firstEnabledAction);
    }

    return ContainerUtil.map(pushActions, action -> new ActionWrapper(myProject, this, action));
  }

  private void customizeDialog(@NotNull SimplePushAction simplePushAction) {
    List<PushDialogCustomizer> customizers = PUSH_DIALOG_CUSTOMIZER_EP.getExtensionList();
    if (!customizers.isEmpty()) {
      if (customizers.size() == 1) {
        PushDialogCustomizer customizer = customizers.get(0);
        customizeDialog(customizer, simplePushAction);
      }
      else {
        LOG.warn("There can be only one push actions customizer, found: " + customizers);
      }
    }
  }

  private void customizeDialog(@NotNull PushDialogCustomizer customizer, @NotNull SimplePushAction simplePushAction) {
    simplePushAction.getTemplatePresentation().setText(customizer.getNameForSimplePushAction(this));
    simplePushAction.setCondition(customizer.getCondition());
  }

  @Nullable
  @Override
  protected Border createContentPaneBorder() {
    return null;
  }

  @Nullable
  @Override
  protected JPanel createSouthAdditionalPanel() {
    return createSouthOptionsPanel();
  }

  @Override
  protected JComponent createSouthPanel() {
    JComponent southPanel = super.createSouthPanel();
    southPanel.setBorder(JBUI.Borders.empty(8, 12));
    return southPanel;
  }

  @Override
  protected JComponent createCenterPanel() {
    JPanel panel = JBUI.Panels.simplePanel(0, 2)
      .addToCenter(myListPanel)
      .addToBottom(createOptionsPanel());
    myListPanel.setPreferredSize(new JBDimension(CENTER_PANEL_WIDTH, CENTER_PANEL_HEIGHT));
    return panel;
  }

  @NotNull
  protected JPanel createOptionsPanel() {
    JPanel optionsPanel = new OptionsPanel();
    for (VcsPushOptionsPanel panel : myAdditionalPanels.values()) {
      if (panel.getPosition() == VcsPushOptionsPanel.OptionsPanelPosition.DEFAULT) {
        optionsPanel.add(panel);
      }
    }
    return optionsPanel;
  }

  @NotNull
  private JPanel createSouthOptionsPanel() {
    JPanel optionsPanel =
      new JPanel(new MigLayout(String.format("ins 0 %spx 0 0, flowx, gapx %spx", JBUI.scale(20), JBUI.scale(16)))); //NON-NLS
    for (VcsPushOptionsPanel panel : myAdditionalPanels.values()) {
      if (panel.getPosition() == VcsPushOptionsPanel.OptionsPanelPosition.SOUTH) {
        optionsPanel.add(panel);
      }
    }
    return optionsPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return DIMENSION_KEY;
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
  protected Action @NotNull [] createActions() {
    final List<Action> actions = new ArrayList<>();
    myMainAction = new ComplexPushAction(myPushActions.get(0), myPushActions.subList(1, myPushActions.size()));
    myMainAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions.add(myMainAction);
    actions.add(getCancelAction());
    actions.add(getHelpAction());
    return actions.toArray(new Action[0]);
  }

  @Override
  public boolean canPush() {
    return myController.isPushAllowed();
  }

  @Override
  public @NotNull Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> getSelectedPushSpecs() {
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
    return myMainAction;
  }

  @Override
  protected String getHelpId() {
    return HELP_ID;
  }

  @Override
  @RequiresEdt
  public void push(boolean forcePush) {
    executeAfterRunningPrePushHandlers(new Task.Backgroundable(myProject, DvcsBundle.message("push.process.pushing"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myController.push(forcePush);
      }
    });
  }

  @Override
  @RequiresEdt
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

  @RequiresEdt
  public PrePushHandler.Result runPrePushHandlersInModalTask() {
    FileDocumentManager.getInstance().saveAllDocuments();
    AtomicReference<PrePushHandler.Result> result = new AtomicReference<>(PrePushHandler.Result.OK);
    new Task.Modal(myController.getProject(), DvcsBundle.message("push.process.checking.commits"), true) {
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

          @Nls String suggestionMessageProblem;
          if (cause instanceof ProcessCanceledException) {
            suggestionMessageProblem = DvcsBundle.message("push.dialog.push.cancelled.message", failedHandler);
          }
          else {
            super.onThrowable(cause);
            suggestionMessageProblem = DvcsBundle.message("push.dialog.push.failed.message", failedHandler);
          }

          @Nls String suggestionMessageQuestion = skippedHandlers.isEmpty()
                                                  ? DvcsBundle.message("push.dialog.push.anyway.confirmation")
                                                  : DvcsBundle.message("push.dialog.skip.all.remaining.steps.confirmation");

          suggestToSkipOrPush(suggestionMessageProblem + "\n" + suggestionMessageQuestion);
        }
        else {
          super.onThrowable(error);
        }
      }

      @Override
      public void onCancel() {
        super.onCancel();
        suggestToSkipOrPush(DvcsBundle.message("push.dialog.skip.all.steps.confirmation"));
      }

      private void suggestToSkipOrPush(@Nls @NotNull String message) {
        if (Messages.showOkCancelDialog(myProject,
                                        message,
                                        DvcsBundle.message("action.push"),
                                        DvcsBundle.message("action.push.anyway"),
                                        IdeBundle.message("button.cancel"),
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
    myMainAction.setEnabled(canPush());
    for (ActionWrapper wrapper : myPushActions) {
      wrapper.update();
    }
  }

  public void enableOkActions(boolean value) {
    myMainAction.setEnabled(value);
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

  private static final class ComplexPushAction extends AbstractAction implements OptionAction {
    private final ActionWrapper myDefaultAction;
    private final List<? extends ActionWrapper> myOptions;

    private ComplexPushAction(@NotNull ActionWrapper defaultAction, @NotNull List<? extends ActionWrapper> additionalActions) {
      super(defaultAction.getName());
      myDefaultAction = defaultAction;
      myOptions = additionalActions;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myDefaultAction.actionPerformed(e);
    }

    @Override
    public void setEnabled(boolean isEnabled) {
      super.setEnabled(isEnabled);
      for (Action optionAction : myOptions) {
        optionAction.setEnabled(isEnabled);
      }
    }

    @Override
    public Action @NotNull [] getOptions() {
      return myOptions.toArray(new ActionWrapper[0]);
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

    @Nls
    @NotNull
    public String getName() {
      return requireNonNull(myRealAction.getTemplatePresentation().getTextWithMnemonic());
    }
  }

  private static class OptionsPanel extends JPanel {
    OptionsPanel() {
      super(new MigLayout("ins 0 0, flowy"));
    }

    @Override
    public Component add(Component comp) {
      JPanel wrapperPanel = new BorderLayoutPanel().addToCenter(comp);
      wrapperPanel.setBorder(JBUI.Borders.empty(5, 15, 0, 0));
      return super.add(wrapperPanel);
    }
  }
}
