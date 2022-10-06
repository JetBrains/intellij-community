// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NormalPatternsCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_19;
  }

  @NeedsIndex.Full
  public void testPatternInInstanceOf() {
    myFixture.configureByText("a.java", """
      record Point(int x, int y);
      class X {
        void test(Object o) {
          if(o instanceof Poi<caret>)
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            record Point(int x, int y);
                            class X {
                              void test(Object o) {
                                if(o instanceof Point(int x, int y))
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testPatternInInstanceOfNameConflict() {
    myFixture.configureByText("a.java", """
      record Point(int x, int y);
      class X {
        void test(Object o) {
          String x, y, y1;
          if(o instanceof Poi<caret>)
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x1, int y2)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            record Point(int x, int y);
                            class X {
                              void test(Object o) {
                                String x, y, y1;
                                if(o instanceof Point(int x1, int y2))
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFile() {
    myFixture.addClass("package com.example; public record Point(int x, int y) {}");
    myFixture.configureByText("a.java", """
      class X {
        void test(Object o) {
          if(o instanceof Poi<caret>)
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            import com.example.Point;

                            class X {
                              void test(Object o) {
                                if(o instanceof Point(int x, int y))
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFileTwoImports() {
    myFixture.addClass("package com.example; public record Age(int years) {}");
    myFixture.addClass("package com.example; public record Person(String name, Age age) {}");
    myFixture.configureByText("a.java", """
      class X {
        void test(Object o) {
          if(o instanceof Pers<caret>)
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Person", "Person(String name, Age age)"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            import com.example.Age;
                            import com.example.Person;
                                                         
                            class X {
                              void test(Object o) {
                                if(o instanceof Person(String name, Age age))
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFileNested() {
    myFixture.addClass("package com.example; public class Y { public record Point(int x, int y) {} }");
    myFixture.configureByText("a.java", """
      class X {
        void test(Object o) {
          if(o instanceof Poi<caret>)
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            import com.example.Y;
                                                         
                            class X {
                              void test(Object o) {
                                if(o instanceof Y.Point(int x, int y))
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testPatternInSwitch() {
    myFixture.configureByText("a.java", """
      record Point(int x, int y);
      class X {
        void test(Object o) {
          switch(o) {
            case Poi<caret>
          }
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            record Point(int x, int y);
                            class X {
                              void test(Object o) {
                                switch(o) {
                                  case Point(int x, int y)
                                }
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testNoPatternInSwitchTypeMismatch() {
    myFixture.configureByText("a.java", """
      record Point(int x, int y);
      class X {
        void test(String o) {
          switch(o) {
            case Poi<caret>
          }
        }
      }""");
    myFixture.completeBasic();
    // Options contributed by TypoTolerantMatcher; these classes contain nested public classes,
    // so we allow them, as they may potentially contain string constants
    assertEquals(List.of("ForkJoinPool", "ThreadPoolExecutor"), myFixture.getLookupElementStrings());
  }

  @NeedsIndex.Full
  public void testPatternInSwitchAnotherFile() {
    myFixture.addClass("package com.example; public record Point(int x, int y) {}");
    myFixture.configureByText("a.java", """
      class X {
        void test(Object o) {
          switch(o) {
            case Poi<caret>
          }
        }
      }""");
    myFixture.completeBasic();
    assertEquals(List.of("Point", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
    selectItem(1);
    myFixture.checkResult("""
                            import com.example.Point;
                                                         
                            class X {
                              void test(Object o) {
                                switch(o) {
                                  case Point(int x, int y)
                                }
                              }
                            }""");
  }

  public void testPatternInTypePosition() {
    myFixture.configureByText("a.java", """
      class X {
        final class StrIncompatible {}
        record MyRecord(CharSequence comp) {}
        void test(Object o) {
          int StrVar = 1;
          if (o instanceof MyRecord(Str<caret> s))
        }
      }""");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "StrIncompatible", "String", "StrictMath", "StringBuffer", "StringBuilder");
  }

  public void testPatternInTypePosition2() {
    myFixture.configureByText("a.java", """
      class X {
        final class StrIncompatible {}
        record MyRecord(CharSequence comp) {}
        void test(Object o) {
          int StrVar = 1;
          if (o instanceof MyRecord(Str<caret>))
        }
      }""");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "StrIncompatible", "String", "StrictMath", "StringBuffer", "StringBuilder");
  }

  private void selectItem(int index) {
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);
    myFixture.getLookup().setCurrentItem(elements[index]);
    myFixture.type('\n');
  }
}
