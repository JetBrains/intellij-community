package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;

public interface UsageGroupingRule {
  UsageGroupingRule[] EMPTY_ARRAY = new UsageGroupingRule[0];
  UsageGroup groupUsage(Usage usage);
}
