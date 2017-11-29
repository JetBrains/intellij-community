/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.execution.actions;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Set;

public class ConfigurationFromEditorTest extends LightCodeInsightFixtureTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit; public @interface Test{}");
  }

  private JUnitConfiguration setupConfigurationContext(final String fileText) {
    myFixture.configureByText("MyTest.java", fileText);

    MapDataContext dataContext = new MapDataContext();
    dataContext.put(CommonDataKeys.PROJECT, myFixture.getProject());
    dataContext.put(CommonDataKeys.EDITOR, myFixture.getEditor());
    dataContext.put(CommonDataKeys.PSI_FILE, myFixture.getFile());

    ConfigurationContext context = ConfigurationContext.getFromContext(dataContext);
    RunnerAndConfigurationSettings settings = context.getConfiguration();
    return (JUnitConfiguration)settings.getConfiguration();
  }

  public void testPatternConfigurationFromSelection() {
    JUnitConfiguration configuration = setupConfigurationContext("import org.junit.Test; public class MyTest {\n" +
                                                                 "<selection>@Test\n" +
                                                                 "public void t1(){}\n" +

                                                                 "@Test\n" +
                                                                 "public void t2(){}\n" +
                                                                 "</selection>" +

                                                                 "@Test\n" +
                                                                 "public void t3(){}\n" +

                                                                 "}");
    Set<String> patterns = configuration.getPersistentData().getPatterns();
    assertSameElements(patterns, "MyTest,t1", "MyTest,t2");
  }

  public void testPatternConfigurationFromMultipleCarets() {
    JUnitConfiguration configuration = setupConfigurationContext("import org.junit.Test; public class MyTest {\n" +
                                                                 "@Test\n" +
                                                                 "public void t<caret>1(){}\n" +

                                                                 "@Test\n" +
                                                                 "public void t<caret>2(){}\n" +

                                                                 "@Test\n" +
                                                                 "public void t3(){}\n" +

                                                                 "}");
    Set<String> patterns = configuration.getPersistentData().getPatterns();
    assertSameElements(patterns, "MyTest,t1", "MyTest,t2");
  }
}
