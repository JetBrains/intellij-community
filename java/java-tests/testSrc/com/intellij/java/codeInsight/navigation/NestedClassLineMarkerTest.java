// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NestedClassLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  private final Set<RunnerAndConfigurationSettings> myTempSettings = new HashSet<>();
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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
  }

  public void testNestedClassInAbstractOuter() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class <caret>NestedTests {
                                       @Test void myTest() {}    }
                                }
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'TemplateTest$NestedTests'");
    assertEquals("ConcreteTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_CLASS, data.TEST_OBJECT);
    assertEquals("ConcreteTest$NestedTests", data.MAIN_CLASS_NAME);
  }

  public void testAbstractOuterWithSingleInheritor() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                   @Test void myTe<caret>st() {}}
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("ConcreteTest", data.MAIN_CLASS_NAME);
  }
  
  public void testAbstractOuterWithSingleInheritorInNested() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest {
                                  @Test void my<caret>Test() {}
                                  static class ConcreteTest extends TemplateTest {}
                                }
                                """
    );
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$ConcreteTest", data.MAIN_CLASS_NAME);
  }
  
  public void testMethodInStaticNestedInheritorClassInAbstractOuter() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    static class NestedTests extends TemplateTest {
                                       @Test <caret> void myTest() {}
                                    }
                                    static class NestedTests1 extends TemplateTest {
                                       @Test void myTest() {}
                                    }
                                }
                                """);
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }
  
  public void testMethodInNestedClassInAbstractOuter() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class NestedTests {
                                       @Test <caret> void myTest() {}    }
                                }
                                class ConcreteTest extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("ConcreteTest.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("ConcreteTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  public void testMethodInStaticClassInAbstractOuter() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Test void markerTest() {}
                                    static class NestedTests {
                                       @Test <caret> void myTest() {}    }
                                }
                                class ConcreteTest1 extends TemplateTest { }
                                class ConcreteTest2 extends TemplateTest { }""");
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }
  
  public void testMethodInNestedInheritorClassInAbstractOuter() {
    myFixture.configureByText("MyTest.java",
                              """
                                import org.junit.jupiter.api.Nested;
                                import org.junit.jupiter.api.Test;
                                abstract class TemplateTest{
                                    @Nested
                                    class NestedTests extends TemplateTest {
                                       @Test <caret> void myTest() {}    }
                                    @Nested
                                    class NestedTests1 extends TemplateTest {
                                       @Test void myTest() {}    }
                                }
                                """);
    RunConfiguration configuration = startConfigurationFromGutter("Run 'myTest()'");
    assertEquals("TemplateTest$NestedTests.myTest", configuration.getName());
    JUnitConfiguration.Data data = ((JUnitConfiguration)configuration).getPersistentData();
    assertEquals(JUnitConfiguration.TEST_METHOD, data.TEST_OBJECT);
    assertEquals("TemplateTest$NestedTests", data.MAIN_CLASS_NAME);
    assertEquals("myTest", data.METHOD_NAME);
  }

  @NotNull
  private RunConfiguration startConfigurationFromGutter(String expectedRunTitle) {
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    ActionGroup group = mark.getPopupMenuActions();
    assertNotNull(group);
    TestActionEvent event = new TestActionEvent();
    List<AnAction> list = ContainerUtil.findAll(group.getChildren(event), action -> {
      TestActionEvent actionEvent = new TestActionEvent();
      action.update(actionEvent);
      String text = actionEvent.getPresentation().getText();
      return text != null && text.startsWith("Run '") && text.endsWith("'");
    });
    assertEquals(list.toString(), 1, list.size());
    list.get(0).update(event);
    assertEquals(expectedRunTitle, event.getPresentation().getText());
    myFixture.testAction(list.get(0));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(getProject()).getSelectedConfiguration();
    myTempSettings.add(selectedConfiguration);
    RunConfiguration configuration = selectedConfiguration.getConfiguration();
    assertInstanceOf(configuration, JUnitConfiguration.class);
    return configuration;
  }
}
