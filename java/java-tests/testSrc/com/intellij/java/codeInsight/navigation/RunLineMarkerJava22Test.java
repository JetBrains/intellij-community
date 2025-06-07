// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.icons.AllIcons;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
      assertEquals(1, marks.size());
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

  public void testImpossibleInheritStatic() {
    IdeaTestUtil.withLevel(getModule(), getEnabledLevel(), () -> {
      myFixture.addClass("""
                             public class AAAAAA {
                                 public AAAAAA(int a){}
                           
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
      assertEquals(1, marks.size());
      GutterMark mark = marks.get(0);
      checkMark(mark, "BBBBBB");
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

  private static void checkMark(@NotNull GutterMark mark2, @NotNull String className) {
    assertTrue(mark2 instanceof LineMarkerInfo.LineMarkerGutterIconRenderer);
    LineMarkerInfo.LineMarkerGutterIconRenderer gutterIconRenderer2 = (LineMarkerInfo.LineMarkerGutterIconRenderer)mark2;
    PsiElement element2 = gutterIconRenderer2.getLineMarkerInfo().getElement();
    assertEquals(AllIcons.RunConfigurations.TestState.Run, gutterIconRenderer2.getIcon());
    assertTrue(element2 instanceof PsiIdentifier);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element2, PsiClass.class);
    assertEquals(className, psiClass.getName());
  }
}
