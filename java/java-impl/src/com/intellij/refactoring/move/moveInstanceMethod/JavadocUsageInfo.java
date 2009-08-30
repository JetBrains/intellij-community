package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.usageView.UsageInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;

/**
 * @author ven
 */
public class JavadocUsageInfo extends UsageInfo {
  private final PsiDocTagValue myDocTagValue;

  public JavadocUsageInfo(final PsiDocTagValue docTagValue) {
    super(docTagValue);
    myDocTagValue = docTagValue;
  }
}
