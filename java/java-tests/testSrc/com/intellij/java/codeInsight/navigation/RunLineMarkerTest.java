// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.application.options.editor.GutterIconsConfigurable;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationProducer;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerTest extends LightJavaCodeInsightFixtureTestCase {
  private final Set<RunnerAndConfigurationSettings> myTempSettings = new HashSet<>();
  @Override
  protected void tearDown() throws Exception {
    RunManager runManager = RunManager.getInstance(getProject());
    for (RunnerAndConfigurationSettings setting : myTempSettings) {
      runManager.removeConfiguration(setting);
    }
    super.tearDown();
  }

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
  
  public void testRunLineMarkerOnInterface() {
    myFixture.configureByText("Main.java", "public class Ma<caret>in implements I {}\n" +
                                           "interface I {" +
                                           "    public static void main(String[] args) {}\n" +
                                           "}\n");
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

  private void doTestClassWithMain(Runnable setupExisting) {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", "public class <caret>MainTest extends junit.framework.TestCase {\n" +
                                               "    public static void main(String[] args) {\n" +
                                               "    }\n" +
                                               "    public void testFoo() {\n" +
                                               "    }\n" +
                                               "}");
    if (setupExisting != null) {
      setupExisting.run();
    }
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
    myFixture.testAction(list.get(1));
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(getProject()).getSelectedConfiguration();
    myTempSettings.add(selectedConfiguration);
    assertEquals("MainTest", selectedConfiguration.getName());
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

  public void testGeneratedNames() {
    RunManager manager = RunManager.getInstance(getProject());
    ApplicationConfiguration first = new ApplicationConfiguration("Unknown", getProject());
    first.setMainClass(myFixture.addClass("package a; public class Main {public static void main(String[] args) {}}"));
    first.setGeneratedName();
    assertEquals("Main", first.getName());
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, first);
    manager.addConfiguration(settings);
    myTempSettings.add(settings);
    
    ApplicationConfiguration second = new ApplicationConfiguration("Unknown", getProject());
    second.setMainClass(myFixture.addClass("package b; public class Main {public static void main(String[] args) {}}"));
    second.setGeneratedName();
    assertEquals("b.Main", second.getName());
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
    String message = ExecutionBundle.message("create.run.configuration.action.name");
    AnAction action = ContainerUtil.find(children, t -> {
      if (t.getTemplateText() == null) return false;
      return t.getTemplateText().startsWith(message);
    });
    assertNotNull(action);
    myFixture.testAction(action);
    TestActionEvent event = new TestActionEvent();
    action.update(event);
    assertTrue(event.getPresentation().getText().startsWith(message));
    ContainerUtil.addIfNotNull(myTempSettings, RunManager.getInstance(getProject()).getSelectedConfiguration());
  }

  public void testActionNameFromPreferredProducer() {
    myFixture.configureByText("Main.java", "public class Main {\n" +
                                           "    public static void ma<caret>in(String[] args) {}\n" +
                                           "}");
    RunConfigurationProducer.EP_NAME.getPoint().registerExtension(new ApplicationConfigurationProducer() {
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
