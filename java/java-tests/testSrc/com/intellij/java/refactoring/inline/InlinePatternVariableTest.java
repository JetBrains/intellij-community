// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class InlinePatternVariableTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  public void testSimple() { doTest(); }
  public void testSimpleAtRef() { doTest(); }
  public void testTernary() { doTest(); }
  public void testInNestedCondition() { doTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_LATEST;
  }

  private void doTest() {
    String name = getTestName(false);
    String fileName = "/refactoring/inlinePatternVariable/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    new InlineLocalHandler().inlineElement(getProject(), getEditor(), element);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(fileName + ".after");
  }
}
