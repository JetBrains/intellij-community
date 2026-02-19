// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.icons.AllIcons;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RunLineMarkerJava22Test extends LightJavaCodeInsightFixtureTestCase {

  public void testImplicitAllowsNonStatic() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        void main<caret>() {
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
    });
  }

  protected @NotNull LanguageLevel getEnabledLevel() {
    return LanguageLevel.JDK_22_PREVIEW;
  }

  public void testImplicitNotAllowsNonStatic() {
    IdeaTestUtil.withLevel(getModule(), getDisabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        void main<caret>() {
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  protected @NotNull LanguageLevel getDisabledLevel() {
    return LanguageLevel.JDK_22;
  }

  public void testClassWithConstructorWithoutParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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

  public void testClassWithConstructorWithoutParamsAndInstanceMainIsNotAllowed() {
    IdeaTestUtil.withLevel(getModule(), getDisabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        public class A {
          A() {}
          public void main<caret>() {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  public void testClassWithDefaultConstructorParamsAndInstanceMainIsAllowed() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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

  public void testStaticWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        static void main() {}
        void main<caret>(String[] args) {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
      assertNotEmpty(myFixture.findGuttersAtCaret());
    });
  }

  public void testInstanceWithParameterHasHigherPriority() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        class A extends B {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testInstanceMainMethodInSuperInterface() {
    myFixture.addClass("public interface B { default void main() {} }");
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        class A implements B {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(1, marks.size());
    });
  }

  public void testStaticMainMethodInSuperInterface() {
    myFixture.addClass("public interface B { static void main() {} }");
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        class A implements B {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testAbstractInstanceMainMethodInSuperInterface() {
    myFixture.addClass("public interface B {  void main(); }");
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("MainTest.java", """
        abstract class A implements B {}
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testAbstractClass() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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


  public void testStaticMethodsInPreviewWithConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
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

  public void testInheritMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.addClass("""
                             public class AAAAAA {
                                 public void main(String[] args) {
                                     System.out.println("2");
                                 }
                             }
                           """);
      myFixture.configureByText("BBBBBB.java", """
          public class BBBBBB extends AAAAAA {
              public static void <caret>main() {
                  System.out.println("1");
              }
          }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  public void testNonDefaultConstructorParent() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.addClass("""
                             public class AAAAAA {
                                 public AAAAAA(int a){}
                           
                                 public void main(String[] args) {
                                     System.out.println("2");
                                 }
                             }
                           """);
      myFixture.configureByText("Main.java", """
        public class Main extends AAAAAA {
            public Main() {
                super(1);
            }
        
            public static void main<caret>() {
                System.out.println("1");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(1, allMarks.size());
      checkMark(allMarks.get(0), "Main");
    });
  }
public void testImpossibleInheritStaticInterface() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.addClass("""
                             public interface AInterface {
                                static void main(String[] args) {
                                    System.out.println("1");
                                }
                             }
                           """);
      myFixture.configureByText("Main.java", """
        public class Main<caret> implements AInterface {
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEquals(0, marks.size());
    });
  }

  public void testImpossibleCreateClassForNonStaticMethodWithSuperClass() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("BBBBBB.java", """
        class Parent {
            void main(String[] args) {
                System.out.println("non-static, args");
            }
        }
        
        
        class Child extends Parent <caret> {
            Child(int p) {
                System.out.println("Child constructor");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());

      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("void main"));

      marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      GutterMark mark = marks.get(0);
      checkMark(mark, "Parent");
    });
  }

  public void testRunLineMarkerOnInterface25() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Ma<caret>in implements I {}
        interface I {
          public static void main(String[] args) {}
        }
        """);
      assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
      assertEquals(0, myFixture.findGuttersAtCaret().size());
      List<GutterMark> gutters = myFixture.findAllGutters();
      gutters = ContainerUtil.filter(gutters, gutter ->
        gutter instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer &&
        AllIcons.RunConfigurations.TestState.Run.equals(renderer.getIcon()));
      assertEquals(2, gutters.size());
      assertTrue(ContainerUtil.or(gutters, gutter ->
        gutter instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer &&
        "I".equals(renderer.getLineMarkerInfo().getElement().getText())
      ));
      assertTrue(ContainerUtil.or(gutters, gutter ->
        gutter instanceof LineMarkerInfo.LineMarkerGutterIconRenderer<?> renderer &&
        "main".equals(renderer.getLineMarkerInfo().getElement().getText())
      ));
      assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
    });
  }

  public void testImpossibleCreateClassForNonStaticMethod() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("BBBBBB.java", """
        public abstract class AbstractClass {
            static void main() {
                System.out.println("Hello, World!");
            }
        
            void main<caret>(String[] args) {
                System.out.println("Hello, World! no constructor, non-static, args");
            }
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());

      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("static void main"));
      marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());

      myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getEditor().getDocument().getText().indexOf("class AbstractClass"));
      marks = myFixture.findGuttersAtCaret();
      assertEquals(0, marks.size());
    });
  }

  public void testPrivateStaticConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPackageStaticConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testProtectedStaticConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPublicStaticConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public Main() {}
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPrivateConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEmpty(marks);
      checkMethod(myFixture, false);
    });
  }

  public void testPackageConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testProtectedConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPublicConstructor() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public Main() {}
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPrivateMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findAllGutters();
      assertEmpty(marks);
      checkMethod(myFixture, false);
    });
  }

  public void testPackageMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testProtectedMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPublicMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPrivateStaticMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            private static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEmpty(marks);

      checkMethod(myFixture, false);
    });
  }

  public void testPackageStaticMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testProtectedStaticMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            protected static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  public void testPublicStaticMain() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.configureByText("Main.java", """
        public class Main<caret> {
            public static void main(String[] args) {}
        }
        """);
      List<GutterMark> marks = myFixture.findGuttersAtCaret();
      assertEquals(1, marks.size());
      checkMark(marks.get(0), "Main");

      List<GutterMark> allMarks = myFixture.findAllGutters();
      assertEquals(2, allMarks.size());
      checkMark(allMarks.get(0), "Main");
      checkMark(allMarks.get(1), "Main");

      checkMethod(myFixture, true);
    });
  }

  static void checkMark(@NotNull GutterMark mark2, @NotNull String className) {
    assertTrue(mark2 instanceof LineMarkerInfo.LineMarkerGutterIconRenderer);
    LineMarkerInfo.LineMarkerGutterIconRenderer gutterIconRenderer2 = (LineMarkerInfo.LineMarkerGutterIconRenderer)mark2;
    PsiElement element2 = gutterIconRenderer2.getLineMarkerInfo().getElement();
    assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer2.getIcon());
    assertTrue(element2 instanceof PsiIdentifier);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element2, PsiClass.class);
    assertEquals(className, psiClass.getName());
  }

  static void checkMethod(@NotNull JavaCodeInsightTestFixture fixture, boolean isMain) {
    PsiElement atCaret = fixture.getElementAtCaret();
    PsiClass psiClass = PsiTreeUtil.getParentOfType(atCaret, PsiClass.class, false);
    PsiMethod[] mains = psiClass.findMethodsByName("main", false);
    assertEquals(1, mains.length);
    PsiMethod main = mains[0];
    assertNotNull(main);
    assertEquals(isMain, PsiMethodUtil.isMainMethod(main));
  }
}
