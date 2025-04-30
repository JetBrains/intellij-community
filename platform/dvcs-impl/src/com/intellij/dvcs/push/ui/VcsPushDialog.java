// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.*;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.UiDataProvider;
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
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static java.util.Objects.requireNonNull;

public class VcsPushDialog extends DialogWrapper implements VcsPushUi, UiDataProvider {
  private static final @NonNls String DIMENSION_KEY = "Vcs.Push.Dialog.v2";
  private static final @NonNls String HELP_ID = "Vcs.Push.Dialog";
  private static final Logger LOG = Logger.getInstance(VcsPushDialog.class);
  private static final ExtensionPointName<PushDialogCustomizer> PUSH_DIALOG_CUSTOMIZER_EP =
    ExtensionPointName.create("com.intellij.pushDialogCustomizer");
  private static final ExtensionPointName<PushDialogActionsProvider> PUSH_DIALOG_ACTIONS_PROVIDER_EP =
    ExtensionPointName.create("com.intellij.pushDialogActionsProvider");

  private static final int CENTER_PANEL_HEIGHT = 450;
  private static final int CENTER_PANEL_WIDTH = 800;

  protected final Project myProject;
  protected final PushController myController;
  private final Map<PushSupport<?, ?, ?>, VcsPushOptionsPanel> myAdditionalPanels;
  private final @Unmodifiable Map<String, VcsPushOptionsPanel> myCustomPanels;
  private final PushLog myListPanel;
  private final JComponent myTopPanel;

  private final ComplexPushAction myMainAction;
  private final @NotNull List<ActionWrapper> myPushActions;

  public VcsPushDialog(@NotNull Project project,
                       @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo) {
    this(project, VcsRepositoryManager.getInstance(project).getRepositories(), selectedRepositories, currentRepo, null);
  }

  public VcsPushDialog(@NotNull Project project,
                       @NotNull Collection<? extends Repository> allRepos,
                       @NotNull List<? extends Repository> selectedRepositories,
                       @Nullable Repository currentRepo, @Nullable PushSource pushSource) {
    super(project, true, IdeModalityType.IDE);
    myProject = project;
    myController =
      new PushController(project, this, allRepos, selectedRepositories, currentRepo,
                         pushSource);
    myAdditionalPanels = myController.createAdditionalPanels();
    myCustomPanels = myController.createCustomPanels(allRepos);
    myListPanel = myController.getPushPanelLog();
    myTopPanel = myController.createTopPanel();

    myPushActions = collectPushActions();
    myMainAction = new ComplexPushAction(myPushActions.get(0), myPushActions.subList(1, myPushActions.size()));
    myMainAction.putValue(DEFAULT_ACTION, Boolean.TRUE);

    init();
    updateOkActions();
    setOKButtonText(DvcsBundle.message("action.push"));
    String title = allRepos.size() == 1
                   ? DvcsBundle.message("push.dialog.push.commits.to.title", DvcsUtil.getShortRepositoryName(getFirstItem(allRepos)))
                   : DvcsBundle.message("push.dialog.push.commits.title");
    setTitle(title);
  }

  private @NotNull List<ActionWrapper> collectPushActions() {
    ActionManager actionManager = ActionManager.getInstance();
    DefaultActionGroup group = (DefaultActionGroup)actionManager.getAction("Vcs.Push.Actions");
    List<PushActionBase> pushActions = new ArrayList<>(
      ContainerUtil.findAll(group.getChildren(actionManager), PushActionBase.class));

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

  @Override
  protected @Nullable Border createContentPaneBorder() {
    return null;
  }

  @Override
  protected @Nullable JPanel createSouthAdditionalPanel() {
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
    JPanel panel = JBUI.Panels.simplePanel()
      .addToTop(myTopPanel)
      .addToCenter(myListPanel)
      .addToBottom(createOptionsPanel());
    myListPanel.setPreferredSize(new JBDimension(CENTER_PANEL_WIDTH, CENTER_PANEL_HEIGHT));
    return panel;
  }

  protected @NotNull JPanel createOptionsPanel() {
    JPanel optionsPanel = new OptionsPanel();
    optionsPanel.setBorder(JBUI.Borders.emptyTop(2));

    List<VcsPushOptionsPanel> panels = new ArrayList<>(myAdditionalPanels.values());
    panels.addAll(myCustomPanels.values());

    for (VcsPushOptionsPanel panel : panels) {
      if (panel.getPosition() == VcsPushOptionsPanel.OptionsPanelPosition.DEFAULT) {
        optionsPanel.add(panel);
      }
    }
    return optionsPanel;
  }

  private @NotNull JPanel createSouthOptionsPanel() {
    JPanel optionsPanel =
      new JPanel(new MigLayout("ins 0 20 0 0, flowx, gapx 16")); //NON-NLS

    List<VcsPushOptionsPanel> panels = new ArrayList<>(myAdditionalPanels.values());
    panels.addAll(myCustomPanels.values());

    for (VcsPushOptionsPanel panel : panels) {
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

  @Override
  protected @Nullable ValidationInfo doValidate() {
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
  public boolean hasWarnings() {
    return myController.hasCommitWarnings();
  }

  @Override
  public @NotNull Map<PushSupport<Repository, PushSource, PushTarget>, Collection<PushInfo>> getSelectedPushSpecs() {
    return myController.getSelectedPushSpecs();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myListPanel.getPreferredFocusedComponent();
  }

  @Override
  protected @NotNull Action getOKAction() {
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
        if (error instanceof PushController.HandlerException handlerException) {
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
    myMainAction.update();
    for (ActionWrapper wrapper : myPushActions) {
      wrapper.update();
    }
  }

  public void enableOkActions(boolean value) {
    myMainAction.setEnabled(value);
  }

  @Override
  public @Nullable VcsPushOptionValue getAdditionalOptionValue(@NotNull PushSupport support) {
    VcsPushOptionsPanel panel = myAdditionalPanels.get(support);
    return panel == null ? null : panel.getValue();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(VcsPushUi.VCS_PUSH_DIALOG, this);
  }

  @ApiStatus.Experimental
  public @NotNull Map<String, VcsPushOptionValue> getCustomParams() {
    Map<String, VcsPushOptionValue> ret = new HashMap<>();
    myCustomPanels.forEach((id, panel) -> {
      VcsPushOptionValue value = panel.getValue();
      if (value != null) ret.put(id, value);
    });
    return ret;
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

    public void update() {
      VcsPushUi dialog = myDefaultAction.myDialog;
      PushActionBase realAction = myDefaultAction.myRealAction;

      setEnabled(dialog.canPush());
      putValue(Action.NAME, realAction.getText(dialog, enabled));
      putValue(Action.SHORT_DESCRIPTION, realAction.getDescription(dialog, enabled));
    }

    @Override
    public Action @NotNull [] getOptions() {
      return myOptions.toArray(new ActionWrapper[0]);
    }
  }

  private static class ActionWrapper extends AbstractAction {

    private final @NotNull Project myProject;
    private final @NotNull VcsPushUi myDialog;
    private final @NotNull PushActionBase myRealAction;

    ActionWrapper(@NotNull Project project, @NotNull VcsPushUi dialog, @NotNull PushActionBase realAction) {
      myProject = project;
      myDialog = dialog;
      myRealAction = realAction;
      putValue(Action.NAME, myRealAction.getText(myDialog, true));
      putValue(OptionAction.AN_ACTION, realAction);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myRealAction.actionPerformed(myProject, myDialog);
    }

    public void update() {
      boolean enabled = myRealAction.isEnabled(myDialog);
      setEnabled(enabled);
      putValue(Action.NAME, myRealAction.getText(myDialog, enabled));
      putValue(Action.SHORT_DESCRIPTION, myRealAction.getDescription(myDialog, enabled));
    }

    public @Nls @NotNull String getName() {
      return requireNonNull(myRealAction.getTemplatePresentation().getTextWithMnemonic());
    }
  }

  private static class OptionsPanel extends JPanel {
    OptionsPanel() {
      super(new MigLayout("ins 0 0, flowy, gap 0"));
    }

    @Override
    public Component add(Component comp) {
      JPanel wrapperPanel = new BorderLayoutPanel().addToCenter(comp);
      wrapperPanel.setBorder(JBUI.Borders.empty(5, 15, 0, 0));
      return super.add(wrapperPanel);
    }
  }
}
