// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
@IndexingModeTemplate
public class NormalPatternsPrimitiveCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceof() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(Integer x, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(fl<caret>)
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              record Point(Integer x, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(float <caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceof2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(int x, flo<caret>)
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, float <caret>)
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceofBeforeIdentifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, int y);
        class X {
          void test(Object o) {
            if(o instanceof Point(int x, fl<caret> y))
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  if(o instanceof Point(int x, float <caret>y))
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveRecordSwitch() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        record Point(int x, int y);
        class X {
          void test(Object o) {
            switch(o){
              case Point(int x, fl<caret> y) ->
             }
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              record Point(int x, int y);
                              class X {
                                void test(Object o) {
                                  switch(o){
                                    case Point(int x, float y) ->
                                   }
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveAfterCase() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            switch(o){
              case flo<caret> ->
            }
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class X {
                                void test(Object o) {
                                  switch(o){
                                    case float <caret>->
                                  }
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveAfterCaseBeforeIdentifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            switch(o){
              case d<caret> o ->
            }
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class X {
                                void test(Object o) {
                                  switch(o){
                                    case double o ->
                                  }
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveAfterInstanceof() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            if (o instanceof fl<caret>
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class X {
                                void test(Object o) {
                                  if (o instanceof float <caret>
                                }
                              }""");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPrimitiveAfterInstanceofBeforeIdentifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(Object o) {
            if (o instanceof fl<caret> a
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class X {
                                void test(Object o) {
                                  if (o instanceof float <caret>a
                                }
                              }""");
    });
  }

  @Test
  public void testSwitchPatternBoolean() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(boolean o) {
            switch(o){
              case t<caret>
            }
          }
        }""");
      myFixture.completeBasic();
      myFixture.checkResult("""
                              class X {
                                void test(boolean o) {
                                  switch(o){
                                    case true -> <caret>
                                  }
                                }
                              }""");
    });
  }

  @Test
  public void testSwitchPatternAnotherBoolean() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          void test(boolean o) {
            switch(o){
              case true -> System.out.println();
              case <caret>
            }
          }
        }""");
      myFixture.completeBasic();
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      Set<String> lookupStrings = new HashSet<>();
      for (LookupElement element : lookup == null ? new ArrayList<LookupElement>() : lookup.getItems()) {
        lookupStrings.add(element.getLookupString());
      }

      assertThat(lookupStrings).contains("false");
      assertThat(lookupStrings).doesNotContain("true");
    });
  }

  @Test
  public void testSwitchPatternAnotherBooleanWithExpression() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class X {
          private final boolean yes = true;
          void test(boolean o) {
            switch(o){
              case yes -> System.out.println();
              case <caret>
            }
          }
        }""");
      myFixture.completeBasic();
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(myFixture.getEditor());
      Set<String> lookupStrings = new HashSet<>();
      for (LookupElement element : lookup == null ? new ArrayList<LookupElement>() : lookup.getItems()) {
        lookupStrings.add(element.getLookupString());
      }

      assertThat(lookupStrings).contains("false");
      assertThat(lookupStrings).doesNotContain("true");
    });
  }
}
