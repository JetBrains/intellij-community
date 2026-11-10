// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.application.ApplicationRunLineMarkerProvider;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.java.codeInsight.navigation.RunLineMarkerJava22Test.checkMark;
import static com.intellij.java.codeInsight.navigation.RunLineMarkerJava22Test.checkMethod;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class RunLineMarkerJava21Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testBasic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_11, () -> {
      myFixture.configureByText("MainTest.java", """
      class A{
        public static void main<caret>(String[] args) {
        }
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      GutterMark mark = marks.get(0);
      assertTrue(mark instanceof LineMarkerInfo.LineMarkerGutterIconRenderer);
      LineMarkerInfo.LineMarkerGutterIconRenderer gutterIconRenderer = (LineMarkerInfo.LineMarkerGutterIconRenderer)mark;
      ReadAction.runBlocking(() -> {
        PsiElement element = gutterIconRenderer.getLineMarkerInfo().getElement();
        assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.getIcon());
        assertTrue(element instanceof PsiIdentifier);
        assertEquals("main", element.getText());
      });
    });
  }

  @Test
  public void testImplicitAllowsNonStatic() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testClassWithConstructorWithoutParamsAndInstanceMainIsAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        A() {}
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testClassWithDefaultConstructorParamsAndInstanceMainIsAllowed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testClassWithConstructorWithParametersAndInstanceMethodIsNotEntryPoint() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        A(String s) {}
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testMainInsideInnerClassInImplicitClassHasNoGutter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void foo() {
      }
      
      public class A {
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testClassInsideInnerClassInImplicitClassHasNoGutter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void foo() {
      }
      
      public class A<caret> {
        public void main() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testStaticWithParameterHasHigherPriority() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main<caret>() {}
      static void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty();
    });
  }

  @Test
  public void testStaticWithNoParametersHasHigherPriorityThanInstance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main() {}
      void main<caret>(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty();
    });
  }

  @Test
  public void testInstanceWithParameterHasHigherPriority() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {}
      void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertThat(myFixture.findGuttersAtCaret()).isNullOrEmpty();
    });
  }

  @Test
  public void testInstanceMainMethodInSuperClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class B { void main() {} }");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A extends B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testInstanceMainMethodInSuperInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public interface B { default void main() {} }");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testStaticMainMethodInSuperInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public interface B { static void main() {} }");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testStaticMainMethodInSuperClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public class B { public static void main(String[] args) {} }");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  @Test
  public void testAbstractInstanceMainMethodInSuperInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("public interface B {  void main(); }");
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      abstract class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testAbstractClass() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      abstract class A {
        void main(){};
       }
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
        interface A {
          default void main(){};
         }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  @Test
  public void testInstanceMainMethodInInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Run.java", """
        interface Run {
            public default void main(String[] args) {
                System.out.println("Hello from default!");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertThat(marks).isNullOrEmpty();
    });
  }

  @Test
  public void testTwoStaticMainMethods() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Run.java", """
        class Main {
            public static void main(String[] args) {
                System.out.println("main with parameters");
            }
        
            static void main() {
                System.out.println("main without parameters");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(2, marks.size()); // class and one method
    });
  }

  @Test
  public void testStaticMethodsIn21PreviewWithConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Run.java", """
          class Outer {
              Outer(int i) {}
              int hello() {
                  return 1;
              }
        
              public static void main(String[] args) {
        
              }
          }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(2, marks.size()); // class and one method
    });
  }

  @Test
  public void testImplicitClassDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("MainTest.java", """
        void main<caret>() {
        }
        """);
      PsiClass implicitClass = ReadAction.computeBlocking(() -> file.getClasses()[0]);
      PsiMethod mainMethod = ReadAction.computeBlocking(() -> implicitClass.getMethods()[0]);
      ApplicationRunLineMarkerProvider provider = new ApplicationRunLineMarkerProvider();
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
        RunLineMarkerContributor.Info info = ReadAction.computeBlocking(() -> provider.getInfo(mainMethod.getNameIdentifier()));
        assertNotNull(info);
      });
    });
  }

  @Test
  public void testClassWithMainMethodDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("MainTest.java", """
        public class MainTest<caret>{
          public static void main<caret>() {
          }
        }
        """);
      PsiClass psiClass = ReadAction.computeBlocking(() -> file.getClasses()[0]);
      ApplicationRunLineMarkerProvider provider = new ApplicationRunLineMarkerProvider();
      DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
        RunLineMarkerContributor.Info info = ReadAction.computeBlocking(() -> provider.getInfo(psiClass.getNameIdentifier()));
        assertNotNull(info);
      });
    });
  }

  @Test
  public void testPrivateStaticConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }
  @Test
  public void testPackageStaticConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testProtectedStaticConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPublicStaticConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPrivateConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertThat(marks).isNullOrEmpty();
      ReadAction.runBlocking(() -> checkMethod(myFixture, false));
    });
  }

  @Test
  public void testPackageConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testProtectedConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPublicConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPrivateMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertThat(marks).isNullOrEmpty();
      ReadAction.runBlocking(() -> checkMethod(myFixture, false));
    });
  }

  @Test
  public void testPackageMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testProtectedMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPublicMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPrivateStaticMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertThat(marks).isNullOrEmpty();
      ReadAction.runBlocking(() -> checkMethod(myFixture, false));
    });
  }

  @Test
  public void testPackageStaticMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testProtectedStaticMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }

  @Test
  public void testPublicStaticMain() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      ReadAction.runBlocking(() -> checkMark(marks.get(0), "Main"));

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      ReadAction.runBlocking(() -> {
        checkMark(allMarks.get(0), "Main");
        checkMark(allMarks.get(1), "Main");
        checkMethod(myFixture, true);
      });
    });
  }
}
