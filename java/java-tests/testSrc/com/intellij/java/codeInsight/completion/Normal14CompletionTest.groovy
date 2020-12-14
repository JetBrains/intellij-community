// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
class Normal14CompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15
  }

  void testRecordImplements() {
    myFixture.configureByText("a.java", "record X() impl<caret>")
    myFixture.completeBasic()
    myFixture.checkResult("record X() implements ")
  }

  void testRecordNoClassAfterHeader() {
    myFixture.configureByText("a.java", "record X() cla<caret>")
    myFixture.completeBasic()
    myFixture.checkResult("record X() cla")
  }

  void testRecordComponentName() {
    myFixture.configureByText("a.java", "record FooBar(FooBar fo<caret>)")
    myFixture.completeBasic()
    myFixture.checkResult("record FooBar(FooBar fooBar)")
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters generation)")
  void testRecordAccessorDeclaration() {
    doTest()
  }

  void testTopLevelPublicRecord() { doTest() }

  void testTopLevelPublicRecordParenthesisExists() { doTest() }

  void testTopLevelPublicRecordBraceExists() { doTest() }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  void testSealedClassDifferentPackageInheritor() {
    myFixture.addClass("package bar;\nimport foo.*;\npublic final class Child2 extends Parent {}")
    doTest()
  }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  void testSealedClassPermitsReference() { doTest() }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  void testSecondPermitsReference() { doTest() }
}