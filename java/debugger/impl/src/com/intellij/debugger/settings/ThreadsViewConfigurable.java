// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;

import javax.swing.*;

/**
 * @author Eugene Belyaev
 */
public class ThreadsViewConfigurable implements Configurable {
  private final ThreadsViewSettings mySettings;
  private JPanel myPanel;
  private JCheckBox myShowGroupsCheckBox;
  private JCheckBox myLineNumberCheckBox;
  private JCheckBox myClassNameCheckBox;
  private JCheckBox mySourceCheckBox;
  private JCheckBox myShowSyntheticsCheckBox;
  private JCheckBox myShowCurrentThreadChechBox;
  private JCheckBox myPackageCheckBox;
  private JCheckBox myArgsTypesCheckBox;
  private final CompositeDataBinding myDataBinding = new CompositeDataBinding();

  public ThreadsViewConfigurable(ThreadsViewSettings settings) {
    mySettings = settings;

    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_CLASS_NAME", myClassNameCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_PACKAGE_NAME", myPackageCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_ARGUMENTS_TYPES", myArgsTypesCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_LINE_NUMBER", myLineNumberCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_SOURCE_NAME", mySourceCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_THREAD_GROUPS", myShowGroupsCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_SYNTHETIC_FRAMES", myShowSyntheticsCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_CURRENT_THREAD", myShowCurrentThreadChechBox));
  }

  @Override
  public String getDisplayName() {
    return JavaDebuggerBundle.message("threads.view.configurable.display.name");
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void apply() {
    myDataBinding.saveData(mySettings);
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      for (DebuggerSession session : (DebuggerManagerEx.getInstanceEx(project)).getSessions()) {
        (session).refresh(false);
      }
      XDebuggerUtilImpl.rebuildAllSessionsViews(project);
    }
  }

  @Override
  public void reset() {
    myDataBinding.loadData(mySettings);
  }

  @Override
  public boolean isModified() {
    return myDataBinding.isModified(mySettings);
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.customizeThreadView";
  }
}
