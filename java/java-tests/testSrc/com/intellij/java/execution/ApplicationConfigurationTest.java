// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.RunConfigurationConfigurableAdapter;
import com.intellij.execution.application.ApplicationConfigurable;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.ui.CommonJavaParametersPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;

public class ApplicationConfigurationTest extends BaseConfigurationTestCase {
  public void testCreatingApplicationConfiguration() throws ConfigurationException {
    if (PlatformTestUtil.COVERAGE_ENABLED_BUILD) return;

    ApplicationConfiguration configuration = new ApplicationConfiguration(null, myProject);
    ApplicationConfigurable editor = new ApplicationConfigurable(myProject);
    try {
      editor.getComponent(); // To get all the watchers installed.
      Configurable configurable = new RunConfigurationConfigurableAdapter(editor, configuration);
      configurable.reset();
      CommonJavaParametersPanel javaParameters = editor.getCommonProgramParameters();
      javaParameters.setProgramParameters("prg");
      javaParameters.setVMParameters("vm");
      javaParameters.setWorkingDirectory("dir");
      assertTrue(configurable.isModified());
      configurable.apply();
      assertEquals("prg", configuration.getProgramParameters());
      assertEquals("vm", configuration.getVMParameters());
      assertEquals("dir", configuration.getWorkingDirectory());
    }
    finally {
      Disposer.dispose(editor);
    }
  }
}
