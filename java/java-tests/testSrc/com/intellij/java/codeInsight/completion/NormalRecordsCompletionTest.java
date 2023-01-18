// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

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

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassDifferentPackageInheritor() {
    myFixture.addClass("package bar;\nimport foo.*;\npublic final class Child2 extends Parent {}");
    doTest();
  }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassPermitsReference() { doTest(); }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSecondPermitsReference() { doTest(); }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedPermitsInner() { doTest("\n"); }
}
