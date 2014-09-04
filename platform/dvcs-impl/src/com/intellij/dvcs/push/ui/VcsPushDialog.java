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
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.openapi.ui.ValidationInfo;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.ui.Messages.OK;

public class VcsPushDialog extends DialogWrapper {

  @NotNull private final Project myProject;
  private final PushLog myListPanel;
  private final PushController myController;
  private final Action[] myExecutorActions = {new DvcsPushAction("&Force Push", true)};
  @NotNull private final JPanel myAdditionalOptionsFromVcsPanel;

  private DvcsPushAction myPushAction;

  public VcsPushDialog(@NotNull Project project, @NotNull List<? extends Repository> selectedRepositories) {
    super(project);
    myProject = project;
    myController = new PushController(project, this, selectedRepositories);
    myListPanel = myController.getPushPanelLog();
    myAdditionalOptionsFromVcsPanel = new JPanel(new MigLayout("ins 0 0, flowx"));
    init();
    setOKButtonText("Push");
    setOKButtonMnemonic('P');
    setTitle("Push Dialog");
  }

  @Override
  protected JComponent createCenterPanel() {

    JComponent rootPanel = new JPanel(new BorderLayout(0, 15));
    rootPanel.add(myListPanel, BorderLayout.CENTER);
    for (VcsPushOptionsPanel panel : myController.getAdditionalPanels()) {
      myAdditionalOptionsFromVcsPanel.add(panel);
    }
    rootPanel.add(myAdditionalOptionsFromVcsPanel, BorderLayout.SOUTH);
    return rootPanel;
  }

  @Override
  protected String getDimensionServiceKey() {
    return VcsPushDialog.class.getName();
  }

  @Override
  @NotNull
  protected Action[] createActions() {
    final List<Action> actions = new ArrayList<Action>();
    myPushAction = new DvcsPushAction("&Push", false);
    myPushAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    actions.add(myPushAction);
    myPushAction.setOptions(myExecutorActions);
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

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return myController.validate();
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.push.dialog";
  }

  public void updateButtons() {
    initValidation();
  }

  @Override
  protected boolean postponeValidation() {
    return false;
  }

  private class DvcsPushAction extends AbstractAction implements OptionAction {
    private Action[] myOptions = new Action[0];
    private final boolean myForce;

    private DvcsPushAction(String title, boolean force) {
      super(title);
      myForce = force;
    }

    @Override
    public void setEnabled(boolean isEnabled) {
      super.setEnabled(isEnabled);
      for (Action optionAction : myOptions) {
        optionAction.setEnabled(isEnabled);
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (myForce) {
        int answer = Messages.showOkCancelDialog(myProject, getConfirmationMessage(),
                                                 "Force Push",
                                                 "&Force Push", CommonBundle.getCancelButtonText(), Messages.getWarningIcon());
        if (answer != OK) return;
      }
      myController.push(myForce);
      close(OK_EXIT_CODE);
    }

    @NotNull
    @Override
    public Action[] getOptions() {
      return myOptions;
    }

    public void setOptions(Action[] actions) {
      myOptions = actions;
    }
  }

  @NotNull
  private static String getConfirmationMessage() {
    return "You're going to force push. It will overwrite commits at the remote. Are you sure you want to proceed?";
  }
}
