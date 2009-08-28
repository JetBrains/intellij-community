package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.util.IncorrectOperationException;

/**
 * @author yole
 */
public interface SafeDeleteCustomUsageInfo {
  void performRefactoring() throws IncorrectOperationException;
}
