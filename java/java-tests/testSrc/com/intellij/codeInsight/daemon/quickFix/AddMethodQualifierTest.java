/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodQualifierFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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
      new TreeSet<>(ContainerUtil.map(addMethodQualifierFix.getCandidates(), new Function<PsiNamedElement, String>() {
        @Override
        public String fun(final PsiNamedElement psiNamedElement) {
          final String name = psiNamedElement.getName();
          assertNotNull(name);
          return name;
        }
      }));
    final Set<String> expectedCandidatesNames = new TreeSet<>(ContainerUtil.list(candidatesNames));
    assertEquals(expectedCandidatesNames, actualCandidatesNames);
  }

  @Nullable
  private AddMethodQualifierFix getQuickFix() {
    final List<IntentionAction> availableIntentions = myFixture.getAvailableIntentions();
    AddMethodQualifierFix addMethodQualifierFix = null;
    for (final IntentionAction availableIntention : availableIntentions) {
      if (availableIntention instanceof AddMethodQualifierFix) {
        addMethodQualifierFix = (AddMethodQualifierFix)availableIntention;
        break;
      }
    }
    return addMethodQualifierFix;
  }

}
