/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.PsiImportStatement;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemoveImportUsageInfo extends FixableUsageInfo{
  private final PsiImportStatement myImportStatement;

  public RemoveImportUsageInfo(PsiImportStatement importStatement) {
    super(importStatement);
    myImportStatement = importStatement;
  }

  public void fixUsage() throws IncorrectOperationException {
    myImportStatement.delete();
  }
}