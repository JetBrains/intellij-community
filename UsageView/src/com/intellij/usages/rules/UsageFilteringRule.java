package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;

public interface UsageFilteringRule {
  UsageFilteringRule[] EMPTY_ARRAY = new UsageFilteringRule[0];

  boolean isVisible(Usage usage);
}
