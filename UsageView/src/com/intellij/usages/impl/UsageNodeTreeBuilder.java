package com.intellij.usages.impl;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageFilteringRule;

import java.util.Enumeration;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:43:30 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageNodeTreeBuilder {
  private GroupNode myRoot;
  private UsageGroupingRule[] myGroupingRules;
  private UsageFilteringRule[] myFilteringRules;

  public UsageNodeTreeBuilder(UsageGroupingRule[] groupingRules, UsageFilteringRule[] filteringRules, GroupNode root) {
    myGroupingRules = groupingRules;
    myFilteringRules = filteringRules;
    myRoot = root;
  }

  public void appendUsages(Usage[] usages) {
    for (int i = 0; i < usages.length; i++) {
      Usage usage = usages[i];
      appendUsage(usage);
    }
  }

  public void setGroupingRules(UsageGroupingRule[] rules) {
    myGroupingRules = rules;
  }

  public void setFilteringRules(UsageFilteringRule[] rules) {
    myFilteringRules = rules;
  }

  public UsageNode appendUsage(Usage usage) {
    for (int idx = 0; idx < myFilteringRules.length; idx++) {
      final UsageFilteringRule rule = myFilteringRules[idx];
      if (!rule.isVisible(usage)) {
        return null;
      }
    }
    GroupNode lastGroupNode = myRoot;
    for (int i = 0; i < myGroupingRules.length; i++) {
      final UsageGroupingRule rule = myGroupingRules[i];
      final UsageGroup group = rule.groupUsage(usage);
      if (group != null) {
        lastGroupNode = lastGroupNode.addGroup(group, i);
      }
    }
    return lastGroupNode.addUsage(usage);
  }

  public void removeAllChildren() {
    Enumeration children = myRoot.children();
    while (children.hasMoreElements()) {
      Object child = children.nextElement();
      if (child instanceof Node) {
        myRoot.remove((Node)child);
      }
    }
  }
}
