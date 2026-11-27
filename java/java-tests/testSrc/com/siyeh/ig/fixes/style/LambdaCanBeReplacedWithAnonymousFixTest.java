/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.style;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.LambdaCanBeReplacedWithAnonymousInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class LambdaCanBeReplacedWithAnonymousFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LambdaCanBeReplacedWithAnonymousInspection()), "style/lambda2anonymous",
                        InspectionGadgetsBundle.message("lambda.can.be.replaced.with.anonymous.quickfix"));

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    ModuleRootModificationUtil.updateModel(myFixture.getModule(), DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse);
  }

  @Test
  public void testSimpleRunnable() {
    doTest();
  }

  @Test
  public void testSimpleRunnableOnArrow() {
    doTest();
  }

  @Test
  public void testWithSubstitution() {
    doTest();
  }
  
  @Test
  public void testSimpleWildcard() {
    doTest();
  }

  @Test
  public void testRenameParams() {
    doTest();
  }

  @Test
  public void testSuperExpr() {
    doTest();
  }

  @Test
  public void testInsertFinal() {
    doTest();
  }

  @Test
  public void testInnerClass() {
    doTest();
  }

  @Test
  public void testInnerClassWithGenericOuter() {
    doTest();
  }

  @Test
  public void testCyclicInference() {
    doTest();
  }

  @Test
  public void testLocalClasses() {
    doTest();
  }

  @Test
  public void testNoFunctionalInterfaceFound() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testAmbiguity() {
    ig.get().assertQuickfixNotAvailable();
  }
  
  @Test
  public void testInsideAnonymous() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testEffectivelyFinal() {
    doTest();
  }

  @Test
  public void testQualifyThis() {
    doTest();
  }

  @Test
  public void testQualifyThis1() {
    doTest();
  }

  @Test
  public void testQualifyThisAndSuperInside() {
    doTest();
  }

  @Test
  public void testStaticCalls() {
    doTest();
  }

  @Test
  public void testCaretAtBody() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testIncorrectReturnStatementWhenLambdaIsVoidCompatibleButExpressionHasReturnValue() {
    doTest();
  }

  @Test
  public void testForbidReplacementWhenParamsOrReturnWouldBeNotDenotableTypes1() {
    ig.get().assertQuickfixNotAvailable();
  }

  @Test
  public void testRemoveRedundantCast() {
    doTest();
  }
  
  @Test
  public void testMalformedPackageName() {
    doTest();
  }

  private void doTest() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    CodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject());
    CommonCodeStyleSettings javaSettings = settings.getCommonSettings(JavaLanguage.INSTANCE);
    boolean oldSetting = javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE;
    try {
      javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = true;
      ig.get().doTest();
    }
    finally {
      javaSettings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = oldSetting;
    }
  }

}
