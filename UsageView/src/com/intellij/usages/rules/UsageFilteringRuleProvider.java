package com.intellij.usages.rules;

import com.intellij.openapi.project.Project;

public abstract class UsageFilteringRuleProvider {
  public abstract UsageFilteringRule[] getActiveRules(Project project);


}
