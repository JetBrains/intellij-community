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
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.extensions.LoadingOrder;
import com.intellij.openapi.util.Ref;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.java.codeInsight.navigation.RunLineMarkerJava22Test.checkMark;

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
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_24, () -> {
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
    String text = mark.getTooltipText().lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text, text.startsWith("""
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
    String text = mark.getTooltipText().lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text, text.startsWith("""
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
    String text = mark.getTooltipText().lines().filter(line -> !line.contains("Profiler")).collect(Collectors.joining("\n"));
    assertTrue(text, text.startsWith("""
                                       Run 'Main.main()'
                                       Debug 'Main.main()'
                                       Run 'Main.main()' with Coverage"""));
  }

  public void testConfigurationContextCache() {
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void ma<caret>in(String[] args) {}
      }""");
    PsiElement element = ((PsiMethod)myFixture.getElementAtCaret()).getNameIdentifier();
    LineMarkerInfo<?> info = new RunLineMarkerProvider().getLineMarkerInfo(element);
    assertNotNull(info);
    ActionGroup group = info.createGutterRenderer().getPopupMenuActions();
    assertNotNull(group);
    getEditor().getCaretModel().moveToOffset(0);
    DataContext dataContext = DataManager.getInstance().getDataContext(getEditor().getComponent());
    AnAction action = ArrayUtil.getLastElement(group.getChildren(TestActionEvent.createTestEvent(dataContext)));
    assertInstanceOf(action, LineMarkerActionWrapper.class);

    AnActionEvent event = TestActionEvent.createTestEvent(dataContext);
    Utils.initUpdateSession(event);
    action.update(event);
    ConfigurationContext sharedContext = DataManager.getInstance().loadFromDataContext(event.getDataContext(), ConfigurationContext.SHARED_CONTEXT);
    PsiElement locationElement = sharedContext.getLocation().getPsiElement();
    assertEquals("main", locationElement.getText());
  }

  public void testLineMarkerActionWrapper() {
    myFixture.configureByText("Main.java", """
      public class Main {
          public static void ma<caret>in(String[] args) {}
      }""");
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
    DataContext dataContext = SimpleDataContext.getProjectContext(getProject());
    wrapper.update(TestActionEvent.createTestEvent(dataContext));
    assertTrue(updated.get());

    wrapper.actionPerformed(TestActionEvent.createTestEvent(dataContext));
    assertTrue(performed.get());
  }

  public void testNestedNonStaticClassMethod() {
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

  public void testNonStaticMethod() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25, () -> {
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

  public void testInheritStaticMethod() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25, () -> {
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

  public void testInheritMethod() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_25, () -> {
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
