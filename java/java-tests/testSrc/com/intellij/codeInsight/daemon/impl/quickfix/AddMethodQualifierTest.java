// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addMethodCallQualifier/";
  }

  public void testNonStaticMethod() {
    doTest("localElement1", "paramElement", "fieldElement", "staticElement");
  }

  public void testStaticMethod() {
    doTest("localElement1", "paramElement", "staticElement");
  }

  public void testNestedMethod() {
    doTest("localElement1", "nestedParamElement", "nestedField", "paramElement", "fieldElement", "staticElement");
  }

  public void testConstructor() {
    doTest("localElement1", "fieldElement", "staticElement");
  }

  public void testStaticInitializer() {
    doTest("localElement1", "staticElement");
  }

  public void testFix() {
    doTestFix();
  }

  public void testFixArguments() {
    doTestFix();
  }

  public void testNotAvailableIfQualifierExists() {
    myFixture.configureByFile(getTestName(false) + ".java");
    assertNull(getQuickFix());
  }

  private void doTestFix() {
    myFixture.configureByFile(getTestName(false) + ".java");
    IntentionAction action = myFixture.findSingleIntention("Add ");
    myFixture.checkPreviewAndLaunchAction(action);
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest(final String... candidatesNames) {
    UiInterceptors.register(new ChooserInterceptor(List.of(candidatesNames), candidatesNames[0]));
    doTestFix();
  }

  @Nullable
  private AddMethodQualifierFix getQuickFix() {
    final List<IntentionAction> availableIntentions = myFixture.getAvailableIntentions();
    AddMethodQualifierFix addMethodQualifierFix = null;
    for (IntentionAction action : availableIntentions) {
      action = IntentionActionDelegate.unwrap(action);
      if (action instanceof AddMethodQualifierFix) {
        addMethodQualifierFix = (AddMethodQualifierFix)action;
        break;
      }
    }
    return addMethodQualifierFix;
  }

}
