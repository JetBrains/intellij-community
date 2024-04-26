// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.application.options.editor.GutterIconsConfigurable;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationProducer;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class RunLineMarkerTest extends LineMarkerTestCase {

  public void testRunLineMarker() {
    myFixture.configureByText("MainTest.java", """
      public class MainTest {
          public static void <caret>foo(String[] args) {
            someCode();
          }
           public static void main(String[] args) {
            someCode();
          }
      }""");
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertEquals(0, myFixture.findGuttersAtCaret().size());
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(2, gutters.size());
    assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }
  
  public void testRunLineMarkerOnInterface() {
    myFixture.configureByText("Main.java", """
      public class Ma<caret>in implements I {}
      interface I {    public static void main(String[] args) {}
      }
      """);
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertEquals(0, myFixture.findGuttersAtCaret().size());
    List<GutterMark> gutters = myFixture.findAllGutters();
    assertEquals(2, gutters.size());
    assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testNoRunLineMarkerAnonymous() {
    myFixture.configureByText("X.java", """
      public class X {
        void foo() {
          new Object() {
            public static void <caret>main(String[] args) {}
          };
        }
      }""");
    doTestNoRunLineMarkers();
  }

  public void testNoRunLineMarkerLocal() {
    myFixture.configureByText("X.java", """
      public class X {
        void foo() {
          class Local {
            public static void <caret>main(String[] args) {}
          };
        }
      }""");
    doTestNoRunLineMarkers();
  }

  public void testNoRunLineMarker() {
    myFixture.configureByText("MainTest.java", "public class MainTest {}");
    doTestNoRunLineMarkers();
  }

  private void doTestNoRunLineMarkers() {
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertEmpty(myFixture.findAllGutters());
    assertEquals(ThreeState.NO, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testMarkersBeforeRunning() {
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class MainTest extends junit.framework.TestCase {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
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
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void m<caret>ain(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = mark.getTooltipText();
    assertTrue(text.startsWith("""
                                 Run 'Main.main()'
                                 Debug 'Main.main()'
                                 Run 'Main.main()' with Coverage"""));
  }

  public void testTooltipWithUnderscores() {
    myFixture.configureByText("Main_class_test.java", """
      public class Main_class_test {
          public static void m<caret>ain(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = mark.getTooltipText();
    assertTrue(text.startsWith("""
                                 Run 'Main_class_test.main()'
                                 Debug 'Main_class_test.main()'
                                 Run 'Main_class_test.main()' with Coverage"""));
  }

  public void testEditConfigurationAction() {
    myFixture.configureByText("MainTest.java", """
      public class MainTest {
          public static void ma<caret>in(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    AnAction[] children = mark.getPopupMenuActions().getChildren(TestActionEvent.createTestEvent());
    String message = ExecutionBundle.message("create.run.configuration.action.name");
    AnAction action = ContainerUtil.find(children, t -> {
      if (t.getTemplateText() == null) return false;
      return t.getTemplateText().startsWith(message);
    });
    assertNotNull(action);
    myFixture.testAction(action);
    AnActionEvent event = TestActionEvent.createTestEvent();
    action.update(event);
    assertTrue(event.getPresentation().getText().startsWith(message));
    ContainerUtil.addIfNotNull(myTempSettings, RunManager.getInstance(getProject()).getSelectedConfiguration());
  }

  public void testActionNameFromPreferredProducer() {
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void ma<caret>in(String[] args) {}
      }""");
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
    assertTrue(text.startsWith("""
                                 Run 'Main.main()'
                                 Debug 'Main.main()'
                                 Run 'Main.main()' with Coverage"""));
  }
}
