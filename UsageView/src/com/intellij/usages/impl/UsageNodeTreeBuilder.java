package com.intellij.usages.impl;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.rules.UsageGroupingRule;

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
  private UsageGroupingRule[] myRules;

  public UsageNodeTreeBuilder(UsageGroupingRule[] rules, GroupNode root) {
    myRules = rules;
    myRoot = root;
  }

  public void appendUsages(Usage[] usages) {
    for (int i = 0; i < usages.length; i++) {
      Usage usage = usages[i];
      appendUsage(usage);
    }
  }

  public void setRules(UsageGroupingRule[] rules) {
    myRules = rules;
  }

  public UsageNode appendUsage(Usage usage) {
    GroupNode lastGroupNode = myRoot;
    for (int i = 0; i < myRules.length; i++) {
      UsageGroupingRule rule = myRules[i];
      UsageGroup group = rule.groupUsage(usage);
      if (group == null) { continue; }

      lastGroupNode = lastGroupNode.addGroup(group, i);
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
