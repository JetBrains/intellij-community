/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class RunDialog extends DialogWrapper {
  private final Project myProject;
  private final RunConfigurable myConfigurable;
  private JComponent myCenterPanel;
  @NonNls public static String HELP_ID = "reference.dialogs.rundebug";
  private final Executor myExecutor;

  public RunDialog(final Project project, final Executor executor) {
    super(project, true);
    myProject = project;
    myExecutor = executor;

    final String title = executor.getId();
    setTitle(title);

    setOKButtonText(executor.getStartActionText());
    setOKButtonIcon(executor.getIcon());

    myConfigurable = new RunConfigurable(project, this);
    init();
    myConfigurable.reset();
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(),getCancelAction(),new ApplyAction(),getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.execution.impl.RunDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(myCenterPanel);
  }

  protected void doOKAction(){
    try{
      myConfigurable.apply();
    }
    catch(ConfigurationException e){
      Messages.showMessageDialog(myProject, e.getMessage(), ExecutionBundle.message("invalid.data.dialog.title"), Messages.getErrorIcon());
      return;
    }
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    myCenterPanel = myConfigurable.createComponent();
    return myCenterPanel;
  }

  public void setOKActionEnabled(final boolean isEnabled){
    super.setOKActionEnabled(isEnabled);
  }

  protected void dispose() {
    myConfigurable.disposeUIResources();
    super.dispose();
  }

  public static boolean editConfiguration(final Project project, final RunnerAndConfigurationSettings configuration, final String title) {
    return editConfiguration(project, configuration, title, null, null);
  }

  public static boolean editConfiguration(final Project project, final RunnerAndConfigurationSettings configuration, final String title, final String okText, final Icon okIcon) {
    final SingleConfigurationConfigurable<RunConfiguration> configurable = SingleConfigurationConfigurable.editSettings(configuration);
    final SingleConfigurableEditor dialog = new SingleConfigurableEditor(project, configurable) {
      {
        if (okIcon != null) setOKButtonIcon(okIcon);
        if (okText != null) setOKButtonText(okText);
      }
    };
    
    dialog.setTitle(title);
    dialog.show();
    return dialog.isOK();
  }

  private class ApplyAction extends AbstractAction {
    public ApplyAction() {
      super(ExecutionBundle.message("apply.action.name"));
    }

    public void actionPerformed(final ActionEvent event) {
      try{
        myConfigurable.apply();
      }
      catch(ConfigurationException e){
        Messages.showMessageDialog(myProject, e.getMessage(), ExecutionBundle.message("invalid.data.dialog.title"), Messages.getErrorIcon());
      }
    }
  }

  public Executor getExecutor() {
    return myExecutor;
  }
}
