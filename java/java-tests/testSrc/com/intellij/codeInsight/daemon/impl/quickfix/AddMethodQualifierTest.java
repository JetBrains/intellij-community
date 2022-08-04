// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Dmitry Batkovich
 */
public class AddMethodQualifierTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addMethodCallQualifier/";
  }

  public void testNonStaticMethod() {
    doTest("fieldElement", "staticElement", "localElement1", "paramElement");
  }

  public void testStaticMethod() {
    doTest("staticElement", "localElement1", "paramElement");
  }

  public void testNestedMethod() {
    doTest("fieldElement", "localElement1", "nestedField", "nestedParamElement", "staticElement", "paramElement");
  }

  public void testConstructor() {
    doTest("fieldElement", "staticElement", "localElement1");
  }

  public void testStaticInitializer() {
    doTest("staticElement", "localElement1");
  }

  public void testFix() {
    doTestFix();
  }

  public void testNotAvailableIfQualifierExists() {
    myFixture.configureByFile(getTestName(false) + ".java");
    assertNull(getQuickFix());
  }

  private void doTestFix() {
    myFixture.configureByFile(getTestName(false) + "Before.java");
    final AddMethodQualifierFix quickFix = getQuickFix();
    assertNotNull(quickFix);
    myFixture.launchAction(quickFix);
    myFixture.checkResultByFile(getTestName(false) + "After.java");
  }

  private void doTest(final String... candidatesNames) {
    myFixture.configureByFile(getTestName(false) + ".java");
    final AddMethodQualifierFix addMethodQualifierFix = getQuickFix();
    if (candidatesNames.length == 0) {
      assertNull(addMethodQualifierFix);
      return;
    }
    assertNotNull(addMethodQualifierFix);
    final Set<String> actualCandidatesNames =
      new TreeSet<>(ContainerUtil.map(addMethodQualifierFix.getCandidates(), (Function<PsiNamedElement, String>)psiNamedElement -> {
        final String name = psiNamedElement.getName();
        assertNotNull(name);
        return name;
      }));
    final Set<String> expectedCandidatesNames = new TreeSet<>(Arrays.asList(candidatesNames));
    assertEquals(expectedCandidatesNames, actualCandidatesNames);
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
