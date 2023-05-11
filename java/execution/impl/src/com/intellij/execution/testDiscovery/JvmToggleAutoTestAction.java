// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery;

import com.intellij.execution.testframework.autotest.AbstractAutoTestManager;
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;

public class JvmToggleAutoTestAction extends ToggleAutoTestAction {
  @Override
  public AbstractAutoTestManager getAutoTestManager(Project project) {
    return JavaAutoRunManager.getInstance(project);
  }

  @Override
  public boolean isDelayApplicable() {
    return Registry.is("trigger.autotest.on.delay", true);
  }
}
