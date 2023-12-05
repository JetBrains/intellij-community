// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.HashSet;
import java.util.Set;

abstract public class LineMarkerTestCase extends LightJavaCodeInsightFixtureTestCase {
  protected final Set<RunnerAndConfigurationSettings> myTempSettings = new HashSet<>();
  @Override
  protected void tearDown() throws Exception {
    try {
      RunManager runManager = RunManager.getInstance(getProject());
      for (RunnerAndConfigurationSettings setting : myTempSettings) {
        runManager.removeConfiguration(setting);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }
}
