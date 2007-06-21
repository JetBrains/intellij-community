/*
 * @author max
 */
package com.intellij.usages.rules;

public interface OrderableUsageGroupingRule extends UsageGroupingRule {
  int getRank();
}