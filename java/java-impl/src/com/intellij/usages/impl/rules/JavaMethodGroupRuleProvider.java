package com.intellij.usages.impl.rules;

import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class JavaMethodGroupRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule(final Project project) {
    return new MethodGroupingRule();
  }
}
