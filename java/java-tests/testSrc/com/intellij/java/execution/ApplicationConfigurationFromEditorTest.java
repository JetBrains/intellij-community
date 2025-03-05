// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.execution;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class ApplicationConfigurationFromEditorTest extends LightJavaCodeInsightFixtureTestCase {
  public void testApplicationConfigurationForUnknownMethod() {
    assertNull(setupConfigurationContext("""
                                           public class Foo {
                                             public static void x<caret>xx(String[] args) {}
                                           }"""));
    assertNotNull(setupConfigurationContext("""
                                              public class Foo {
                                                public static void m<caret>ain(String[] args) {}
                                              }"""));
  }

  private <T> T setupConfigurationContext(final String fileText) {
    myFixture.configureByText("MyTest.java", fileText);

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myFixture.getProject());
    dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
    dataContext.put(CommonDataKeys.PSI_FILE, myFixture.getFile());

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext, ActionPlaces.UNKNOWN);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    //noinspection unchecked
    return settings != null ? (T)settings.getConfiguration() : null;
  }
}
