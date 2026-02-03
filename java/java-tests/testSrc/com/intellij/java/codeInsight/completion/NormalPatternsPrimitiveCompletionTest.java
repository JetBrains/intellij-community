// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class NormalPatternsPrimitiveCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceof() {
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
  }

  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceof2() {
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
  }

  @NeedsIndex.Full
  public void testPatternPrimitiveRecordInstanceofBeforeIdentifier() {
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
  }

  @NeedsIndex.Full
  public void testPrimitiveRecordSwitch() {
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
  }

  @NeedsIndex.Full
  public void testPrimitiveAfterCase() {
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
  }

  @NeedsIndex.Full
  public void testPrimitiveAfterCaseBeforeIdentifier() {
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
  }

  @NeedsIndex.Full
  public void testPrimitiveAfterInstanceof() {
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
  }

  @NeedsIndex.Full
  public void testPrimitiveAfterInstanceofBeforeIdentifier() {
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
  }

  public void testSwitchPatternBoolean() {
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
  }

  public void testSwitchPatternAnotherBoolean() {
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
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(getEditor());
    Set<String> lookupStrings = new HashSet<>();
    for (LookupElement element : lookup == null ? new ArrayList<LookupElement>() : lookup.getItems()) {
      lookupStrings.add(element.getLookupString());
    }

    assertContainsElements(lookupStrings, "false");
    assertDoesntContain(lookupStrings, "true");
  }

  public void testSwitchPatternAnotherBooleanWithExpression() {
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
    LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(getEditor());
    Set<String> lookupStrings = new HashSet<>();
    for (LookupElement element : lookup == null ? new ArrayList<LookupElement>() : lookup.getItems()) {
      lookupStrings.add(element.getLookupString());
    }

    assertContainsElements(lookupStrings, "false");
    assertDoesntContain(lookupStrings, "true");
  }
}
