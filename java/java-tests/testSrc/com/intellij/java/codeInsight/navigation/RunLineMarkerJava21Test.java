// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.application.ApplicationRunLineMarkerProvider;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class RunLineMarkerJava21Test extends LightJavaCodeInsightFixtureTestCase {

  public void testBasic() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, () -> {
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
      PsiElement element = gutterIconRenderer.getLineMarkerInfo().getElement();
      assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer.getIcon());
      assertTrue(element instanceof PsiIdentifier);
      assertEquals("main", element.getText());
    });
  }

  public void testImplicitAllowsNonStatic() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }


  public void testClassWithConstructorWithoutParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testClassWithDefaultConstructorParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      public class A {
        public void main<caret>() {}
      }
      """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  public void testClassWithConstructorWithParametersAndInstanceMethodIsNotEntryPoint() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testMainInsideInnerClassInImplicitClassHasNoGutter() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testClassInsideInnerClassInImplicitClassHasNoGutter() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testStaticWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main<caret>() {}
      static void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testStaticWithNoParametersHasHigherPriorityThanInstance() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      static void main() {}
      void main<caret>(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testInstanceWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      void main<caret>() {}
      void main(String[] args) {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testInstanceMainMethodInSuperClass() {
    myFixture.addClass("public class B { void main() {} }");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A extends B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testInstanceMainMethodInSuperInterface() {
    myFixture.addClass("public interface B { default void main() {} }");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testStaticMainMethodInSuperInterface() {
    myFixture.addClass("public interface B { static void main() {} }");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testStaticMainMethodInSuperClass() {
    myFixture.addClass("public class B { public static void main(String[] args) {} }");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, () -> {
      myFixture.configureByText("MainTest.java", """
      class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testAbstractInstanceMainMethodInSuperInterface() {
    myFixture.addClass("public interface B {  void main(); }");
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      abstract class A implements B {}
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testAbstractClass() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
      abstract class A {
        void main(){};
       }
      """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testInterface() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_22_PREVIEW, () -> {
      myFixture.configureByText("MainTest.java", """
        interface A {
          default void main(){};
         }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testInstanceMainMethodInInterface() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      myFixture.configureByText("Run.java", """
        interface Run {
            public default void main(String[] args) {
                System.out.println("Hello from default!");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEmpty(marks);
    });
  }

  public void testTwoStaticMainMethods() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testStaticMethodsIn21PreviewWithConstructor() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
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

  public void testImplicitClassDumbMode() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("MainTest.java", """
        void main<caret>() {
        }
        """);
      PsiClass implicitClass = file.getClasses()[0];
      PsiMethod mainMethod = implicitClass.getMethods()[0];
      ApplicationRunLineMarkerProvider provider = new ApplicationRunLineMarkerProvider();
      DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
        RunLineMarkerContributor.Info info = provider.getInfo(mainMethod.getNameIdentifier());
        assertNotNull(info);
      });
    });
  }

  public void testClassWithMainMethodDumbMode() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, () -> {
      PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("MainTest.java", """
        public class MainTest<caret>{
          public static void main<caret>() {
          }
        }
        """);
      PsiClass psiClass = file.getClasses()[0];
      ApplicationRunLineMarkerProvider provider = new ApplicationRunLineMarkerProvider();
      DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
        RunLineMarkerContributor.Info info = provider.getInfo(psiClass.getNameIdentifier());
        assertNotNull(info);
      });
    });
  }
}
