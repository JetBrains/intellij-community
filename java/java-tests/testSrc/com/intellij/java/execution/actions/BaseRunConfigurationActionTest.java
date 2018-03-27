/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.execution.actions;

import com.intellij.execution.actions.BaseRunConfigurationAction;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitConfigurationType;
import com.intellij.testFramework.LightIdeaTestCase;

public class BaseRunConfigurationActionTest extends LightIdeaTestCase {
  public void testRunApplicationName() {
    ApplicationConfiguration configuration = new ApplicationConfiguration(null, getProject(), ApplicationConfigurationType.getInstance());

    configuration.setMainClassName("com.comp.AClass");
    configuration.setGeneratedName();
    checkSuggestedName("AClass.main()", configuration);

    configuration.setName("Other name");
    configuration.setNameChangedByUser(true);
    checkSuggestedName("Other name", configuration);

    configuration.setName("1234567890123456789012345 other long name");
    checkSuggestedName("12345678901234567890...", configuration);

    configuration.setMainClassName("com.comp.A12345678901234567890123LongName");
    configuration.setGeneratedName();
    checkSuggestedName("A1234567890123....main()", configuration);
  }

  public void testRunTestMethodName() {
    JUnitConfiguration configuration = new JUnitConfiguration(null, getProject(), JUnitConfigurationType.getInstance().getConfigurationFactories()[0]);
    JUnitConfiguration.Data data = configuration.getPersistentData();

    data.MAIN_CLASS_NAME = "com.comp.ATestClass";
    configuration.setGeneratedName();
    checkSuggestedName("ATestClass", configuration);

    configuration.setName("Other name");
    configuration.setNameChangedByUser(true);
    checkSuggestedName("Other name", configuration);

    data.METHOD_NAME = "testSmth";
    data.TEST_OBJECT = JUnitConfiguration.TEST_METHOD;
    configuration.setGeneratedName();
    checkSuggestedName("testSmth()", configuration);

    data.METHOD_NAME = "123456789012345678900";
    configuration.setGeneratedName();
    checkSuggestedName("123456789012345678...()", configuration);
  }

  private static void checkSuggestedName(String expectedName, LocatableConfiguration configuration) {
    String suggestedName = BaseRunConfigurationAction.suggestRunActionName(configuration);
    assertEquals(expectedName, suggestedName);
  }
}
