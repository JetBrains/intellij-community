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
package com.intellij.java.codeInsight.navigation;

import com.intellij.application.options.editor.GutterIconsConfigurable;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationProducer;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testIntegration.TestRunLineMarkerProvider;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRunLineMarker() {
    myFixture.configureByText("MainTest.java", "public class MainTest {\n" +
                                               "    public static void <caret>foo(String[] args) {\n" +
                                               "      someCode();\n" +
                                               "    }\n " +
                                               "    public static void main(String[] args) {\n" +
                                               "      someCode();\n" +
                                               "    }\n" +
                                               "}");
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertEquals(0, myFixture.findGuttersAtCaret().size());
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(2, gutters.size());
    assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testNoRunLineMarker() {
    myFixture.configureByText("MainTest.java", "public class MainTest {}");
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertEmpty(myFixture.findAllGutters());
    assertEquals(ThreeState.NO, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testTestClassWithMain() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class <caret>MainTest extends junit.framework.TestCase {\n" +
                                               "    public static void main(String[] args) {\n" +
                                               "    }\n" +
                                               "    public void testFoo() {\n" +
                                               "    }\n" +
                                               "}");
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
    assertEquals(list.toString(), 2, list.size());
    list.get(0).update(event);
    assertEquals("Run 'MainTest.main()'", event.getPresentation().getText());
    list.get(1).update(event);
    assertEquals("Run 'MainTest'", event.getPresentation().getText());
  }

  public void testAbstractTestClassMethods() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MyTest.java", "public abstract class MyTest extends junit.framework.TestCase {\n" +
                                               "    public void test<caret>Foo() {\n" +
                                               "    }\n" +
                                               "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testMarkersBeforeRunning() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class MainTest extends junit.framework.TestCase {\n" +
                                               "    public void test<caret>Foo() {\n" +
                                               "    }\n" +
                                               "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  public void testTestAnnotationInSuperMethodOnly() {
    myFixture.addClass("package org.junit; public @interface Test {}");
    myFixture.addClass("class Foo { @Test public void testFoo() {}}");
    myFixture.configureByText("MyTest.java", "public class MyTest extends Foo {\n" +
                                               "    public void test<caret>Foo() {\n" +
                                               "    }\n" +
                                               "}");
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
      PsiFile file = myFixture.configureByText("MainTest.java", "public class Main {\n" +
                                                                "  public class Main<caret>Test extends junit.framework.TestCase {\n" +
                                                                "    public void testFoo() {\n" +
                                                                "    }\n" +
                                                                "  }" +
                                                                "}");

      RunLineMarkerContributor.Info info = new TestRunLineMarkerProvider().getInfo(file.findElementAt(myFixture.getCaretOffset()));
      assertNotNull(info);
      assertEquals(AllIcons.RunConfigurations.TestState.Red2, info.icon);
    }
    finally {
      stateStorage.removeState(testUrl);
    }
  }

  public void testConfigurable() {
    GutterIconsConfigurable configurable = new GutterIconsConfigurable();
    configurable.createComponent();
    List<GutterIconDescriptor> descriptors = configurable.getDescriptors();
    Set<String> strings = ContainerUtil.map2Set(descriptors, GutterIconDescriptor::getId);
    assertEquals(descriptors.size(), strings.size());
  }

  public void testTooltip() {
    myFixture.configureByText("Main.java", "public class Main {\n" +
                                           "    public static void m<caret>ain(String[] args) {\n" +
                                           "      someCode();\n" +
                                           "    }\n" +
                                           "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = mark.getTooltipText();
    assertTrue(text.startsWith("Run 'Main.main()'\n" +
                               "Debug 'Main.main()'\n" +
                               "Run 'Main.main()' with Coverage"));
  }

  public void testTooltipWithUnderscores() {
    myFixture.configureByText("Main_class_test.java", "public class Main_class_test {\n" +
                                                      "    public static void m<caret>ain(String[] args) {\n" +
                                                      "      someCode();\n" +
                                                      "    }\n" +
                                                      "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = mark.getTooltipText();
    assertTrue(text.startsWith("Run 'Main_class_test.main()'\n" +
                               "Debug 'Main_class_test.main()'\n" +
                               "Run 'Main_class_test.main()' with Coverage"));
  }

  public void testEditConfigurationAction() {
    myFixture.configureByText("MainTest.java", "public class MainTest {\n" +
                                               "    public static void ma<caret>in(String[] args) {\n" +
                                               "      someCode();\n" +
                                               "    }\n" +
                                               "}");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    AnAction[] children = mark.getPopupMenuActions().getChildren(new TestActionEvent());
    AnAction action = ContainerUtil.find(children, t -> t.getTemplateText() != null && t.getTemplateText().startsWith("Create"));
    assertNotNull(action);
    myFixture.testAction(action);
    TestActionEvent event = new TestActionEvent();
    action.update(event);
    assertTrue(event.getPresentation().getText().startsWith("Edit"));
  }

  public void testActionNameFromPreferredProducer() {
    myFixture.configureByText("Main.java", "public class Main {\n" +
                                           "    public static void ma<caret>in(String[] args) {}\n" +
                                           "}");
    RunConfigurationProducer.EP_NAME.getPoint(null).registerExtension(new ApplicationConfigurationProducer() {
      @Override
      protected boolean setupConfigurationFromContext(@NotNull ApplicationConfiguration configuration,
                                                      @NotNull ConfigurationContext context,
                                                      @NotNull Ref<PsiElement> sourceElement) {
        boolean result = super.setupConfigurationFromContext(configuration, context, sourceElement);
        if (result) {
          configuration.setName("Foo");
          configuration.setMainClassName("FooMain");
        }
        return result;
      }

      @Override
      public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return false;
      }
    }, LoadingOrder.FIRST, getTestRootDisposable());
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = mark.getTooltipText();
    assertTrue(text.startsWith("Run 'Main.main()'\n" +
                               "Debug 'Main.main()'\n" +
                               "Run 'Main.main()' with Coverage"));
  }
}
