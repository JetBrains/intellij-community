package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageInfo;

/**
 * @author yole
 */
public interface RefactoringHelper<T> {
  ExtensionPointName<RefactoringHelper> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.helper");

  T prepareOperation(UsageInfo[] usages);
  void performOperation(final Project project, T operationData);
}
