package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import javax.swing.*;
import java.util.Iterator;

/**
 * @author Eugene Belyaev
 */
public class ThreadsViewConfigurable extends BaseConfigurable {
  private ThreadsViewSettings mySettings;
  private JPanel myPanel;
  private JCheckBox myShowGroupsCheckBox;
  private JCheckBox myLineNumberCheckBox;
  private JCheckBox myClassNameCheckBox;
  private JCheckBox mySourceCheckBox;
  private JCheckBox myShowSyntheticsCheckBox;
  private JCheckBox myShowCurrentThreadChechBox;
  private CompositeDataBinding myDataBinding = new CompositeDataBinding();

  public ThreadsViewConfigurable(ThreadsViewSettings settings) {
    mySettings = settings;

    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_CLASS_NAME", myClassNameCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_LINE_NUMBER", myLineNumberCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_SOURCE_NAME", mySourceCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_THREAD_GROUPS", myShowGroupsCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_SYNTHETIC_FRAMES", myShowSyntheticsCheckBox));
    myDataBinding.addBinding(new ToggleButtonBinding("SHOW_CURRENT_THREAD", myShowCurrentThreadChechBox));
  }

  public String getDisplayName() {
    return DebuggerBundle.message("threads.view.configurable.display.name");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public Icon getIcon() {
    return null;
  }

  public void apply() {
    myDataBinding.saveData(mySettings);
    final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (int i = 0; i < openProjects.length; i++) {
      Project project = openProjects[i];
      for (Iterator iterator = (DebuggerManagerEx.getInstanceEx(project)).getSessions().iterator(); iterator.hasNext();) {
        ((DebuggerSession)iterator.next()).refresh(false);
      }
    }
  }

  public void reset() {
    myDataBinding.loadData(mySettings);
  }

  public boolean isModified() {
    return myDataBinding.isModified(mySettings);
  }

  public String getHelpTopic() {
    return "reference.dialogs.customizeThreadView";
  }

  public void disposeUIResources() {
  }
}