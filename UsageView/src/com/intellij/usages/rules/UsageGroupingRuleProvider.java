package com.intellij.usages.rules;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.usages.UsageView;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 27, 2004
 * Time: 8:19:16 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageGroupingRuleProvider extends ApplicationComponent{
  UsageGroupingRule[] getActiveRules(Project project);

  AnAction[] createGroupingActions(UsageView view);
}
