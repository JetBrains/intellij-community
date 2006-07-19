/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.rules.UsageFilteringRule;
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
  private UsageGroupingRule[] myGroupingRules;
  private UsageFilteringRule[] myFilteringRules;

  public UsageNodeTreeBuilder(UsageGroupingRule[] groupingRules, UsageFilteringRule[] filteringRules, GroupNode root) {
    myGroupingRules = groupingRules;
    myFilteringRules = filteringRules;
    myRoot = root;
  }

  public void appendUsages(Usage[] usages) {
    for (Usage usage : usages) {
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
    for (final UsageFilteringRule rule : myFilteringRules) {
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
