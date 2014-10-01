/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.dvcs.push.ui;

import com.intellij.CommonBundle;
import com.intellij.dvcs.push.PushController;
import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OptionAction;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.ui.Messages.OK;

public class VcsPushDialog extends DialogWrapper {

  private static final String ID = "Vcs.Push.Dialog";

  @NotNull private final Project myProject;
  private final PushLog myListPanel;
  private final PushController myController;
  private final Map<PushSupport, VcsPushOptionsPanel> myAdditionalPanels;

  private Action myPushAction;
  @Nullable private ForcePushAction myForcePushAction;

  public VcsPushDialog(@NotNull Project project, @NotNull List<? extends Repository> selectedRepositories) {
    super(project);
    myProject = project;
    myController = new PushController(project, this, selectedRepositories);
    myAdditionalPanels = myController.createAdditionalPanels();
    myListPanel = myController.getPushPanelLog();

    init();
    enableOkActions(myController.isPushAllowed());
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Push Commits");
  }

  @Override
  protected JComponent createCenterPanel() {
    JComponent rootPanel = new JPanel(new BorderLayout(0, 15));
    rootPanel.add(myListPanel, BorderLayout.CENTER);
    JPanel optionsPanel = new JPanel(new MigLayout("ins 0 0, flowx"));
    for (VcsPushOptionsPanel panel : myAdditionalPanels.values()) {
      optionsPanel.add(panel);
    }
    rootPanel.add(optionsPanel, BorderLayout.SOUTH);
    return rootPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return ID;
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    final List<Action> actions = new ArrayList<Action>();
    if (myController.isForcePushEnabled()) {
      myForcePushAction = new ForcePushAction();
      myForcePushAction.setEnabled(myController.isForcePushAllowed());
      myPushAction = new ComplexPushAction(myForcePushAction);
    }
    else {
      myPushAction = new SimplePushAction();
    }
    myPushAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions.add(myPushAction);
    actions.add(getCancelAction());
    actions.add(getHelpAction());
    return actions.toArray(new Action[actions.size()]);
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

  public void enableOkActions(boolean isEnabled) {
    myPushAction.setEnabled(isEnabled);
    if (myForcePushAction != null) {
      myForcePushAction.setEnabled(isEnabled && myController.isForcePushAllowed());
    }
  }

  @Nullable
  public VcsPushOptionValue getAdditionalOptionValue(@NotNull PushSupport support) {
    VcsPushOptionsPanel panel = myAdditionalPanels.get(support);
    return panel == null ? null : panel.getValue();
  }

  private class SimplePushAction extends AbstractAction {
    SimplePushAction() {
      super("&Push");
    }
    @Override
    public void actionPerformed(ActionEvent e) {
      myController.push(false);
      close(OK_EXIT_CODE);
    }
  }

  private class ForcePushAction extends AbstractAction {
    ForcePushAction() {
      super("&Force Push");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      int answer = Messages.showOkCancelDialog(myProject,
                                  "You're going to force push. It will overwrite commits at the remote. Are you sure you want to proceed?",
                                  "Force Push", "&Force Push", CommonBundle.getCancelButtonText(), Messages.getWarningIcon());
      if (answer == OK) {
        myController.push(true);
        close(OK_EXIT_CODE);
      }
    }
  }

  private class ComplexPushAction extends SimplePushAction implements OptionAction {
    private final Action[] myOptions;

    private ComplexPushAction(Action additionalAction) {
      myOptions = new Action[] { additionalAction };
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
