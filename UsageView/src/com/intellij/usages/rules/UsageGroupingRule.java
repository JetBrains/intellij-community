package com.intellij.usages.rules;

import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:25:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface UsageGroupingRule {
  UsageGroup groupUsage(Usage usage);
}
