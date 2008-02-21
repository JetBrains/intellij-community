package com.intellij.ide.impl.dataRules;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.usages.UsageView;
import com.intellij.usageView.UsageInfo;

import java.util.Collections;

/**
 * @author cdr
 */
public class UsageInfo2ListRule implements GetDataRule {
  @Nullable
  public Object getData(final DataProvider dataProvider) {
    UsageInfo usageInfo = (UsageInfo)dataProvider.getData(UsageView.USAGE_INFO_KEY.getName());
    if (usageInfo != null) return Collections.singletonList(usageInfo);
    return null;
  }
}
