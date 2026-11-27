// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodQualifierFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Dmitry Batkovich
 */
@TestApplication
public class AddMethodQualifierTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addMethodCallQualifier/");

  private String testName;

  @BeforeEach
  void setUpTestName(TestInfo testInfo) {
    testName = PlatformTestUtil.getTestName(testInfo.getTestMethod().orElseThrow().getName(), false);
  }

  @Test
  public void testNonStaticMethod() {
    doTest("localElement1", "paramElement", "fieldElement", "staticElement");
  }

  @Test
  public void testStaticMethod() {
    doTest("localElement1", "paramElement", "staticElement");
  }

  @Test
  public void testNestedMethod() {
    doTest("localElement1", "nestedParamElement", "nestedField", "paramElement", "fieldElement", "staticElement");
  }

  @Test
  public void testConstructor() {
    doTest("localElement1", "fieldElement", "staticElement");
  }

  @Test
  public void testStaticInitializer() {
    doTest("localElement1", "staticElement");
  }

  @Test
  public void testFix() {
    doTestFix();
  }

  @Test
  public void testFixArguments() {
    doTestFix("Add qualifier ");
  }

  @Test
  public void testNotAvailableIfQualifierExists() {
    codeInsightFixture.get().configureByFile(testName + ".java");
    assertNull(getQuickFix());
  }

  private void doTestFix() {
    doTestFix("Add method qualifier");
  }

  private void doTestFix(String intentionHint) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(testName + ".java");
    IntentionAction action = myFixture.findSingleIntention(intentionHint);
    EdtTestUtil.runInEdtAndWait(() -> myFixture.checkPreviewAndLaunchAction(action));
    myFixture.checkResultByFile(testName + "_after.java");
  }

  private void doTest(final String... candidatesNames) {
    UiInterceptors.register(new ChooserInterceptor(List.of(candidatesNames), candidatesNames[0]));
    doTestFix();
  }

  @Nullable
  private IntentionAction getQuickFix() {
    final List<IntentionAction> availableIntentions = codeInsightFixture.get().getAvailableIntentions();
    for (IntentionAction action : availableIntentions) {
      if (action.asModCommandAction() instanceof AddMethodQualifierFix) {
        return action;
      }
    }
    return null;
  }

}
