package com.intellij.usages.rules;

import com.intellij.openapi.project.Project;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 27, 2004
 * Time: 8:19:16 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class UsageGroupingRuleProvider {
  public abstract UsageGroupingRule[] getActiveRules(Project project);
}
