package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.usages.UsageView;

public interface UsageFilteringRuleProvider extends ApplicationComponent{
  UsageFilteringRule[] getActiveRules(Project project);

  AnAction[] createFilteringActions(UsageView view);
}
