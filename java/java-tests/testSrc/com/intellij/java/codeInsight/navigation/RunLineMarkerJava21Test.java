// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation;

import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.List;

public class RunLineMarkerJava21Test extends LightJavaCodeInsightFixtureTestCase {

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
}
