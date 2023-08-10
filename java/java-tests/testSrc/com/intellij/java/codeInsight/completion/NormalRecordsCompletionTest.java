// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class NormalRecordsCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testRecordImplements() {
    myFixture.configureByText("a.java", "record X() impl<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("record X() implements ");
  }

  public void testRecordNoClassAfterHeader() {
    myFixture.configureByText("a.java", "record X() cla<caret>");
    myFixture.completeBasic();
    myFixture.checkResult("record X() cla");
  }

  public void testRecordComponentName() {
    myFixture.configureByText("a.java", "record FooBar(FooBar fo<caret>)");
    myFixture.completeBasic();
    myFixture.checkResult("record FooBar(FooBar fooBar)");
  }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfSpecificType() {
    myFixture.configureByText("a.java", """
      interface I<T> { }

      class J<T> implements I<T> {
        void test(I<String> i) {
          if (i instanceof <caret>)
        }
      }""");
    myFixture.completeBasic();
    assert !myFixture.getLookupElementStrings().contains("T");
    myFixture.type("\n");
    myFixture.checkResult("""
                            interface I<T> { }

                            class J<T> implements I<T> {
                              void test(I<String> i) {
                                if (i instanceof J<String><caret>)
                              }
                            }""");
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters generation)")
  public void testRecordAccessorDeclaration() {
    doTest();
  }

  public void testTopLevelPublicRecord() { doTest(); }

  public void testTopLevelPublicRecordParenthesisExists() { doTest(); }

  public void testTopLevelPublicRecordBraceExists() { doTest(); }

  @NeedsIndex.SmartMode(reason = "To avoid merging")
  public void testInsertKnownConstructorParameter() {
    doTestWithHints();
  }

  @NeedsIndex.SmartMode(reason = "To avoid merging")
  public void testInsertKnownConstructorParameterVoid() {
    doTestWithHints();
  }

  private void doTestWithHints() {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean old = settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION;
    settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = true;
    try {
      doTest("\n");
    }
    finally {
      settings.SHOW_PARAMETER_NAME_HINTS_ON_COMPLETION = old;
    }
  }
}
