/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages.impl.rules;

import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.Usage;
import com.intellij.usages.ReadWriteAccessUsage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class ReadAccessFilteringRule implements UsageFilteringRule{
  public boolean isVisible(Usage usage) {
    if (usage instanceof ReadWriteAccessUsage) {
      final ReadWriteAccessUsage readWriteAccessUsage = (ReadWriteAccessUsage)usage;
      final boolean isForReadingOnly = readWriteAccessUsage.isAccessedForReading() && !readWriteAccessUsage.isAccessedForWriting();
      return !isForReadingOnly;
    }
    return true;
  }
}
