package com.intellij.usages.rules;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.usages.impl.UsageViewImpl;

public abstract class UsageFilteringRuleProvider {
  public abstract UsageFilteringRule[] getActiveRules(Project project);

  public abstract AnAction[] createFilteringActions(UsageViewImpl view);
}
