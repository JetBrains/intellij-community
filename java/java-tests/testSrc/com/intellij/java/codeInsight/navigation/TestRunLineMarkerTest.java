// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testIntegration.TestRunLineMarkerProvider;
import com.intellij.util.containers.ContainerUtil;

import java.util.Date;
import java.util.List;

public class TestRunLineMarkerTest extends LineMarkerTestCase {
  public void testAbstractTestClassMethods() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MyTest.java", """
      public abstract class MyTest extends junit.framework.TestCase {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testNestedTestClass() {
    TestStateStorage stateStorage = TestStateStorage.getInstance(getProject());
    String testUrl = "java:suite://Main$MainTest";
    try {
      stateStorage.writeState(testUrl, new TestStateStorage.Record(TestStateInfo.Magnitude.FAILED_INDEX.getValue(), new Date(), 0, 0, "",
                                                                   "", ""));
      myFixture.addClass("package junit.framework; public class TestCase {}");
      PsiFile file = myFixture.configureByText("MainTest.java", """
        public class Main {
          public static class Main<caret>Test extends junit.framework.TestCase {
            public void testFoo() {
            }
          }}""");

      RunLineMarkerContributor.Info info = new TestRunLineMarkerProvider().getInfo(file.findElementAt(myFixture.getCaretOffset()));
      assertNotNull(info);
      assertEquals(AllIcons.RunConfigurations.TestState.Red2, info.icon);
    }
    finally {
      stateStorage.removeState(testUrl);
    }
  }

  public void testTestAnnotationInSuperMethodOnly() {
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("class Foo { @Test public void testFoo() {}}");
    myFixture.configureByText("MyTest.java", """
      public class MyTest extends Foo {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testTestClassWithMain() {
    doTestClassWithMain(null);
  }

  public void testTestClassWithMainTestConfigurationExists() {
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(getProject());
      JUnitConfiguration test = new JUnitConfiguration("MainTest", getProject());
      test.beClassConfiguration(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      myTempSettings.add(settings);
    });
  }

  public void testTestClassWithMainMainConfigurationExists() {
    doTestClassWithMain(() -> {
      RunManager manager = RunManager.getInstance(getProject());
      ApplicationConfiguration test = new ApplicationConfiguration("MainTest.main()", getProject());
      test.setMainClass(myFixture.findClass("MainTest"));
      RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, test);
      manager.addConfiguration(settings);
      myTempSettings.add(settings);
    });
  }

  public void testDisabledTestMethodWithGradleConfiguration() {
    doTestWithDisabledAnnotation(new MockGradleRunConfiguration(myFixture.getProject(), "DisabledMethodTest"), 0);
  }

  public void testDisabledTestMethodWithJunitConfiguration() {
    doTestWithDisabledAnnotation(new JUnitConfiguration("DisabledMethodTest", myFixture.getProject()), 1);
  }

  private void doTestWithDisabledAnnotation(RunConfiguration configuration, int marksCount) {
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(myFixture);
    myFixture.addClass("package org.junit.jupiter.api; public @interface Disabled {}");

    RunManager manager = RunManager.getInstance(myFixture.getProject());
    RunnerAndConfigurationSettings runnerAndConfigurationSettings =
      new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager,configuration);
    manager.addConfiguration(runnerAndConfigurationSettings);
    myTempSettings.add(runnerAndConfigurationSettings);
    manager.setSelectedConfiguration(runnerAndConfigurationSettings);

    myFixture.configureByText("DisabledMethodTest.java", """
      import org.junit.jupiter.api.Disabled;
      import org.junit.jupiter.api.Test;
      class DisabledMethodTest {
        @Disabled
        @Test
        public void testDisabled<caret>() {}
      }
      """);
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(marksCount, marks.size());
  }

  private void doTestClassWithMain(Runnable setupExisting) {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class <caret>MainTest extends junit.framework.TestCase {
          public static void main(String[] args) {
          }
          public void testFoo() {
          }
      }""");
    if (setupExisting != null) {
      setupExisting.run();
    }
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    ActionGroup group = mark.getPopupMenuActions();
    assertNotNull(group);
    AnActionEvent event = TestActionEvent.createTestEvent();
    List<AnAction> list = ContainerUtil.findAll(group.getChildren(event), action -> {
      AnActionEvent actionEvent = TestActionEvent.createTestEvent();
      action.update(actionEvent);
      String text = actionEvent.getPresentation().getText();
      return text != null && text.startsWith("Run '") && text.endsWith("'");
    });
    assertEquals(list.toString(), 2, list.size());
    list.get(0).update(event);
    assertEquals("Run 'MainTest.main()'", event.getPresentation().getText());
    list.get(1).update(event);
    assertEquals("Run 'MainTest'", event.getPresentation().getText());
    myFixture.testAction(list.get(1));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(getProject()).getSelectedConfiguration();
    myTempSettings.add(selectedConfiguration);
    assertEquals("MainTest", selectedConfiguration.getName());
  }
}
