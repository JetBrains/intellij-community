/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.PsiReferenceExpression;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class UsageInfoToUsageConverter {
  public static Usage convert(UsageInfo usageInfo) {
    if (usageInfo.getElement() instanceof PsiReferenceExpression) {
      return new ReadWriteAccessUsageInfo2UsageAdapter(usageInfo);
    }
    return new UsageInfo2UsageAdapter(usageInfo);
  }

  public static Usage[] convert(UsageInfo[] usageInfos) {
    Usage[] usages = new Usage[usageInfos.length];
    for (int i = 0; i < usages.length; i++) {
      usages[i] = convert(usageInfos[i]);
    }
    return usages;
  }
}
