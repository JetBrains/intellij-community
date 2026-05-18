// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution.actions;

import com.intellij.execution.actions.BaseRunConfigurationAction;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.LocatableConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.testFramework.LightIdeaTestCase;

public class BaseRunConfigurationActionTest extends LightIdeaTestCase {
  public void testRunApplicationName() {
    ApplicationConfiguration configuration = new ApplicationConfiguration(null, getProject());

    configuration.setMainClassName("com.comp.AClass");
    configuration.setGeneratedName();
    checkSuggestedName("AClass.main()", configuration);

    configuration.setName("Other name");
    configuration.setNameChangedByUser(true);
    checkSuggestedName("Other name", configuration);

    configuration.setName("12345678101234567820123456783012345678401234567850123456786012345 other long name");
    checkSuggestedName("123456781012345678201234567830123456784012345678501234567860...", configuration);

    configuration.setMainClassName("com.comp.A234567810A234567820A234567830A234567840A234567850A234567860LongName");
    configuration.setGeneratedName();
    checkSuggestedName("A234567810A234567820A234567830A234567840A234567850A234....main()", configuration);
  }

  public void testRunTestMethodName() {
    JUnitConfiguration configuration = new JUnitConfiguration(null, getProject());
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

    data.METHOD_NAME = "123456781012345678201234567830123456784012345678501234567860A";
    configuration.setGeneratedName();
    checkSuggestedName("1234567810123456782012345678301234567840123456785012345678...()", configuration);
  }

  private static void checkSuggestedName(String expectedName, LocatableConfiguration configuration) {
    String suggestedName = BaseRunConfigurationAction.suggestRunActionName(configuration);
    assertEquals(expectedName, suggestedName);
  }
}
