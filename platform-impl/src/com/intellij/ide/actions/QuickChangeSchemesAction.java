package com.intellij.ide.actions;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;

/**
 * @author max
 */
public class QuickChangeSchemesAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, DefaultActionGroup group) {
    final AnAction[] actions = getGroup().getChildren(null);
    for (AnAction action : actions) {
      group.add(action);
    }
  }

  public void actionPerformed(AnActionEvent e) {
    super.actionPerformed(e);
    FeatureUsageTracker.getInstance().triggerFeatureUsed("ui.scheme.quickswitch");
  }

  protected boolean isEnabled() {
    return true;
  }

  private DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_CHANGE_SCHEME);
  }
}
