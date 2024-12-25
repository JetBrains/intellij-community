// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.settings.JavaDebuggerSettings;
import com.intellij.debugger.settings.NodeRendererSettings;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class CustomizeContextViewAction extends XDebuggerTreeActionBase {
  private static int ourLastSelectedTabIndex = 0;

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform(null, "", e);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final Project project = e.getProject();
    final MyTabbedConfigurable configurable = new MyTabbedConfigurable();
    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable) {
      @Override
      protected void doOKAction() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourLastSelectedTabIndex = configurable.getSelectedIndex();
        super.doOKAction();
      }

      @Override
      public void doCancelAction() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourLastSelectedTabIndex = configurable.getSelectedIndex();
        super.doCancelAction();
      }
    };
    editor.show();
  }

  private static class MyTabbedConfigurable extends TabbedConfigurable {
    @Override
    protected @NotNull List<Configurable> createConfigurables() {
      return JavaDebuggerSettings.createDataViewsConfigurable();
    }

    @Override
    public void apply() throws ConfigurationException {
      super.apply();
      NodeRendererSettings.getInstance().fireRenderersChanged();
    }

    @Override
    public String getDisplayName() {
      return JavaDebuggerBundle.message("title.customize.data.views");
    }

    @Override
    public String getHelpTopic() {
      return "reference.debug.customize.data.view";
    }

    @Override
    protected void createConfigurableTabs() {
      for (Configurable configurable : getConfigurables()) {
        JComponent component = configurable.createComponent();
        assert component != null;
        component.setBorder(JBUI.Borders.empty(8, 8));
        myTabbedPane.addTab(configurable.getDisplayName(), component);
      }
      myTabbedPane.setSelectedIndex(ourLastSelectedTabIndex);
    }

    int getSelectedIndex() {
      return myTabbedPane.getSelectedIndex();
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(ActionsBundle.actionText("Debugger.CustomizeContextView"));
    e.getPresentation().setEnabledAndVisible(DebuggerAction.isInJavaSession(e));
  }
}
