// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.application.options.editor.GutterIconsConfigurable;
import com.intellij.codeInsight.daemon.GutterIconDescriptor;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.RunManager;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationProducer;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.lineMarker.LineMarkerActionWrapper;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.java.codeInsight.navigation.RunLineMarkerJava22Test.checkMark;
import static com.intellij.java.codeInsight.navigation.TempRunConfigurationsKt.tempRunConfigurationsFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class RunLineMarkerTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);
  private final TestFixture<TempRunConfigurations> runConfigurations = tempRunConfigurationsFixture(codeInsightFixture);

  @Test
  public void testRunLineMarker() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testRunLineMarkerOnInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_24, () -> {
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
    });
  }

  @Test
  public void testNoRunLineMarkerAnonymous() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testNoRunLineMarkerLocal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testNoRunLineMarker() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MainTest.java", "public class MainTest {}");
    doTestNoRunLineMarkers();
  }

  private void doTestNoRunLineMarkers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    assertThat(myFixture.findAllGutters()).isNullOrEmpty();
    assertEquals(ThreeState.NO, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  @Test
  public void testMarkersBeforeRunning() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package junit.framework; public class TestCase {}");
    myFixture.configureByText("MainTest.java", """
      public class MainTest extends junit.framework.TestCase {
          public void test<caret>Foo() {
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
  }

  @Test
  public void testConfigurable() {
    GutterIconsConfigurable configurable = new GutterIconsConfigurable();
    try {
      configurable.createComponent();
      List<GutterIconDescriptor> descriptors = configurable.getDescriptors();
      Set<String> strings = ContainerUtil.map2Set(descriptors, GutterIconDescriptor::getId);
      assertEquals(descriptors.size(), strings.size());
    }
    finally {
      configurable.disposeUIResources();
    }
  }

  @Test
  public void testGeneratedNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    RunManager manager = RunManager.getInstance(myFixture.getProject());
    ApplicationConfiguration first = new ApplicationConfiguration("Unknown", myFixture.getProject());
    PsiClass aMain = myFixture.addClass("package a; public class Main {public static void main(String[] args) {}}");
    ReadAction.runBlocking(() -> first.setMainClass(aMain));
    first.setGeneratedName();
    assertEquals("Main", first.getName());
    RunnerAndConfigurationSettingsImpl settings = new RunnerAndConfigurationSettingsImpl((RunManagerImpl)manager, first);
    manager.addConfiguration(settings);
    runConfigurations.get().getTempSettings().add(settings);
    
    ApplicationConfiguration second = new ApplicationConfiguration("Unknown", myFixture.getProject());
    PsiClass bMain = myFixture.addClass("package b; public class Main {public static void main(String[] args) {}}");
    ReadAction.runBlocking(() -> second.setMainClass(bMain));
    second.setGeneratedName();
    assertEquals("b.Main", second.getName());
  }

  @Test
  public void testTooltip() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void m<caret>ain(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = ReadAction.computeBlocking(mark::getTooltipText).lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text.startsWith("""
                                       Run 'Main.main()'
                                       Debug 'Main.main()'
                                       Run 'Main.main()' with Coverage"""), text);
  }

  @Test
  public void testTooltipWithUnderscores() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main_class_test.java", """
      public class Main_class_test {
          public static void m<caret>ain(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = ReadAction.computeBlocking(mark::getTooltipText).lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text.startsWith("""
                                       Run 'Main_class_test.main()'
                                       Debug 'Main_class_test.main()'
                                       Run 'Main_class_test.main()' with Coverage"""), text);
  }

  @Test
  public void testEditConfigurationAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("MainTest.java", """
      public class MainTest {
          public static void ma<caret>in(String[] args) {
            someCode();
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    EdtTestUtil.runInEdtAndWait(() -> {
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
      ContainerUtil.addIfNotNull(runConfigurations.get().getTempSettings(), RunManager.getInstance(myFixture.getProject()).getSelectedConfiguration());
    });
  }

  @Test
  public void testActionNameFromPreferredProducer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    }, LoadingOrder.FIRST, myFixture.getTestRootDisposable());
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    GutterIconRenderer mark = (GutterIconRenderer)marks.get(0);
    String text = ReadAction.computeBlocking(mark::getTooltipText).lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text.startsWith("""
                                       Run 'Main.main()'
                                       Debug 'Main.main()'
                                       Run 'Main.main()' with Coverage"""), text);
  }

  @Test
  public void testConfigurationContextCache() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void ma<caret>in(String[] args) {}
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement element = ((PsiMethod)myFixture.getElementAtCaret()).getNameIdentifier();
      LineMarkerInfo<?> info = new RunLineMarkerProvider().getLineMarkerInfo(element);
      assertNotNull(info);
      ActionGroup group = info.createGutterRenderer().getPopupMenuActions();
      assertNotNull(group);
      myFixture.getEditor().getCaretModel().moveToOffset(0);
      DataContext dataContext = DataManager.getInstance().getDataContext(myFixture.getEditor().getComponent());
      AnAction action = ArrayUtil.getLastElement(group.getChildren(TestActionEvent.createTestEvent(dataContext)));
      assertInstanceOf(LineMarkerActionWrapper.class, action);

      AnActionEvent event = TestActionEvent.createTestEvent(dataContext);
      Utils.initUpdateSession(event);
      action.update(event);
      ConfigurationContext sharedContext = DataManager.getInstance().loadFromDataContext(event.getDataContext(), ConfigurationContext.SHARED_CONTEXT);
      PsiElement locationElement = sharedContext.getLocation().getPsiElement();
      assertEquals("main", locationElement.getText());
    });
  }

  @Test
  public void testLineMarkerActionWrapper() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void ma<caret>in(String[] args) {}
      }""");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiElement element = ((PsiMethod)myFixture.getElementAtCaret()).getNameIdentifier();
      Ref<Boolean> updated = Ref.create();
      Ref<Boolean> performed = Ref.create();
      Ref<ConfigurationContext> context = Ref.create();
      LineMarkerActionWrapper wrapper = new LineMarkerActionWrapper(element, new AnAction() {
        @Override
        public void update(@NotNull AnActionEvent e) {
          Location<?> location = e.getData(Location.DATA_KEY);
          assertSame(element, location.getPsiElement());
          context.set(ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace()));
          updated.set(true);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Location<?> location = e.getData(Location.DATA_KEY);
          assertSame(element, location.getPsiElement());
          assertSame(context.get(), ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace()));
          performed.set(true);
        }
      });
      DataContext dataContext = SimpleDataContext.getProjectContext(myFixture.getProject());
      wrapper.update(TestActionEvent.createTestEvent(dataContext));
      assertTrue(updated.get());

      wrapper.actionPerformed(TestActionEvent.createTestEvent(dataContext));
      assertTrue(performed.get());
    });
  }

  @Test
  public void testNestedNonStaticClassMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("A1.java", """
      class A1 {
          class A2{
              public static void <caret>main(String[] args) {
                  System.out.println("1");
              }
          }
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(0, marks.size());
  }

  @Test
  public void testNonStaticMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, () -> {
      myFixture.configureByText("A1.java", """
      class A1 {
          public void main<caret>(){
          }
      }""");
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "A1");
    });
  }

  @Test
  public void testInheritStaticMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, () -> {
      myFixture.addClass("""
                         class A2 {
                             public static void main(String[] args) {
                                 System.out.println("1");
                             }
                         }""");
    myFixture.configureByText("A1.java", """
      class A1<caret> extends A2 {
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    checkMark(marks.get(0), "A1");
    });
  }

  @Test
  public void testInheritMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_25, () -> {
      myFixture.addClass("""
                         class A2 {
                             public void main(String[] args) {
                                 System.out.println("1");
                             }
                         }""");
    myFixture.configureByText("A1.java", """
      class A1<caret> extends A2 {
      }""");
    List<GutterMark> marks = myFixture.findGuttersAtCaret();
    assertEquals(1, marks.size());
    checkMark(marks.get(0), "A1");
    });
  }
}
