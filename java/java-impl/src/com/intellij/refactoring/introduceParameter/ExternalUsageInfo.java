/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;

/**
 * @author dsl
 */
public class ExternalUsageInfo extends UsageInfo {
  public ExternalUsageInfo(PsiElement element) {
    super(element);
  }
}
