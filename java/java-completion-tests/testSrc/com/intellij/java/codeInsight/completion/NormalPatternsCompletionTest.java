// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
@IndexingModeTemplate
public class NormalPatternsCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOf() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, int y);
        class X {
          void test(Object o) {
            if(o instanceof Poi<caret>)
          }
        }""");
      myFixture.completeBasic();
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, int y))
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOfNameConflict() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, int y);
        class X {
          void test(Object o) {
            String x, y, y1;
            if(o instanceof Poi<caret>)
          }
        }""");
      myFixture.completeBasic();
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x1, int y2)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  String x, y, y1;
                                  if(o instanceof Point(int x1, int y2))
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package com.example; public record Point(int x, int y) {}");
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            if(o instanceof Poi<caret>)
          }
        }""");
      myFixture.completeBasic();
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              import com.example.Point;

                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, int y))
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFileTwoImports() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOfAnotherFileNested() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package com.example; public class Y { public record Point(int x, int y) {} }");
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            if(o instanceof Poi<caret>)
          }
        }""");
      myFixture.completeBasic();
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              import com.example.Y;
                            
                              class X {
                                void test(Object o) {
                                  if(o instanceof Y.Point(int x, int y))
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInSwitch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  switch(o) {
                                    case Point(int x, int y)
                                  }
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNoPatternInSwitchTypeMismatch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
      assertEquals(List.of("Policy", "ForkJoinPool", "ThreadPoolExecutor"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInSwitchAnotherFile() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
      assertEquals(List.of("Point", "PolicyQualifierInfo", "Point(int x, int y)", "NullPointerException"), myFixture.getLookupElementStrings());
      selectItem(2);
      myFixture.checkResult("""
                              import com.example.Point;
                            
                              class X {
                                void test(Object o) {
                                  switch(o) {
                                    case Point(int x, int y)
                                  }
                                }
                              }""");
    });
  }

  @Test
  public void testPatternInTypePosition() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testPatternInTypePosition2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

  @Test
  public void testUnnamedVariableAndWhen() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String text = """
        class X {
          void test(Object obj) {
            switch (obj) {
              case String _ when _.isE<caret>
            }
          }
        }
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", text);
      myFixture.completeBasic();
      myFixture.checkResult(text);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDeconstructionListPrimitive() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, String description, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(in<caret>)
          }
        }""");
      myFixture.completeBasic();
      selectItem(1);
      myFixture.checkResult("""
                              record Point(int x, String description, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int <caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testDeconstructionListPrimitive2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, String description, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(int x, String description, in<caret>)
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              record Point(int x, String description, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, String description, int <caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testFullDeconstructionList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, String description, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(in<caret>)
          }
        }""");
      myFixture.completeBasic();
      selectItem(0);
      myFixture.checkResult("""
                              record Point(int x, String description, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, String description, int y<caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testFullDeconstructionListGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point<T>(int x, T description, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point<String>(in<caret>)
          }
        }""");
      myFixture.completeBasic();
      selectItem(0);
      myFixture.checkResult("""
                              record Point<T>(int x, T description, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point<String>(int x, String description, int y<caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testHasTheSameName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x1, int x2);
        class X {
          void test(Object o) {
            int x1 = 0;
            if(o instanceof Point(<caret>)
          }
        }""");
      myFixture.completeBasic();
      selectItem(0);
      myFixture.checkResult(
        """
          record Point(int x1, int x2);
          class X {
            void test(Object o) {
              int x1 = 0;
              if(o instanceof Point(int x2, int x3)
            }
          }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternInInstanceOfMoreThanRecords() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x);
        class X {
          void test(Object o) {
            if(o instanceof Point(int x, <caret>)
          }
        }""");
      myFixture.completeBasic();
    });
  }

  private void selectItem(int index) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    LookupElement[] elements = myFixture.getLookupElements();
    assertNotNull(elements);
    myFixture.getLookup().setCurrentItem(elements[index]);
    myFixture.type('\n');
  }
}
