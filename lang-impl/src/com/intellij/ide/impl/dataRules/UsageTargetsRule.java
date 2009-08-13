package com.intellij.ide.impl.dataRules;

import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class UsageTargetsRule implements GetDataRule {
  @Nullable
  public Object getData(DataProvider dataProvider) {
    return UsageTargetUtil.findUsageTargets(dataProvider);
  }
}