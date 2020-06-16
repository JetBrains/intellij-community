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
    return JAVA_14
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

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters generation)")
  void testRecordAccessorDeclaration() {
    doTest()
  }
}