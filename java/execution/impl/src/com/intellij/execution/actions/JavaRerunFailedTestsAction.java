// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentContainer;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class JavaRerunFailedTestsAction extends AbstractRerunFailedTestsAction {
  public JavaRerunFailedTestsAction(@NotNull ComponentContainer componentContainer, @NotNull TestConsoleProperties consoleProperties) {
    super(componentContainer);
    init(consoleProperties);
  }

  @Override
  protected @NotNull Filter getFilter(@NotNull Project project, @NotNull GlobalSearchScope searchScope) {
    return super.getFilter(project, searchScope).and(new Filter() {
      @Override
      public boolean shouldAccept(AbstractTestProxy test) {
        return test.isLeaf();
      }
    });
  }
}
