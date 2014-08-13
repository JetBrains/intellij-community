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

import com.intellij.dvcs.push.PushController;
import com.intellij.dvcs.push.VcsPushOptionsPanel;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

public class VcsPushDialog extends DialogWrapper {

  private final PushLog myListPanel;
  private final PushController myController;
  private final Action[] myExecutorActions = {new DvcsPushAction("&Force Push", true)};
  @NotNull private final JPanel myAdditionalOptionsFromVcsPanel;

  private DvcsPushAction myPushAction;

  public VcsPushDialog(@NotNull Project project, @NotNull List<? extends Repository> selectedRepositories) {
    super(project);
    myController = new PushController(project, this, selectedRepositories);
    myListPanel = myController.getPushPanelInfo();
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

  protected void setOKActionEnabled(boolean isEnabled) {
    if (myPushAction != null) {
      myPushAction.setEnabled(isEnabled);
    }
    for (Action executorAction : myExecutorActions) {
      executorAction.setEnabled(isEnabled);
    }
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

  @NotNull
  @Override
  protected Action getOKAction() {
    return new DvcsPushAction("&Push", false);
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.push.dialog";
  }

  private class DvcsPushAction extends AbstractAction implements OptionAction {
    private Action[] myOptions = new Action[0];
    private final boolean myForce;

    private DvcsPushAction(String title, boolean force) {
      super(title);
      myForce = force;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
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
}
