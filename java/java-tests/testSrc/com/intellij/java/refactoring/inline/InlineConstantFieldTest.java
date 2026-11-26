// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineConstantFieldHandler;
import com.intellij.refactoring.inline.InlineConstantFieldProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class InlineConstantFieldTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData");

  @Test
  public void testQualifiedExpression() {
    doTest();
  }

  @Test
  public void testQualifiedExpressionInLib() {
    doTest(true);
  }

  @Test
  public void testLocalClassDecoding() {
    doTest();
  }

  @Test
  public void testQualifiedConstantExpression() {
    doTest();
  }

   @Test
   public void testQualifiedConstantExpressionReplacedWithAnotherOne() {
    doTest();
  }
  
  @Test
  public void testStaticallyImportedQualifiedExpression() {
    doTest();
  }

  @Test
  public void testCastWhenLambdaAsQualifier() {
    doTest();
  }

  @Test
  public void testConstantFromLibrary() {
    doTest();
  }

  @Test
  public void testQualifierJava6() {
    doTest();
  }

  @Test
  public void testFinalInitializedInConstructor() {
    doTest();
  }

  @Test
  public void testDiamondInitializer() {
    doTest();
  }

  @Test
  public void testFieldUsedReflectively() {
    doTestConflict("Inlined field is used reflectively");
  }

  @Test
  public void testFieldInitializedWithParameter() {
    doTestConflict("Field initializer refers to parameter <b><code>a</code></b>, which is not accessible in method <b><code>Test.test()</code></b>");
  }

  @Test
  public void testFieldInitializedLocalClass() {
    doTestConflict("Field initializer refers to local class <b><code>Local</code></b>, which is not accessible in method <b><code>Test.test()</code></b>");
  }

  @Test
  public void testFieldInitializedWithParameter1() {
    doTest();
  }

  @Test
  public void testFieldInitializedWithConstant() {
    doTest();
  }

  @Test
  public void testFieldInitializedWithLambda() {
    doTest();
  }

  @Test
  public void testFieldUsedInJavadoc() {
    doTestConflict("Inlined field is used in javadoc");
  }

  @Test
  public void testMultipleInitializers() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/refactoring/inlineConstantField/" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil
        .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
      assertInstanceOf(PsiField.class, element);
      assertNull(InlineConstantFieldHandler.getInitializer((PsiField)element));
    });
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean inlineThisOnly) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    @NonNls String fileName = "/refactoring/inlineConstantField/" + name + ".java";
    myFixture.configureByFile(fileName);
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil
        .findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
      final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      PsiReferenceExpression refExpr = ref instanceof PsiReferenceExpression ? (PsiReferenceExpression)ref : null;
      assertInstanceOf(PsiField.class, element);
      PsiField field = (PsiField)element.getNavigationElement();
      new InlineConstantFieldProcessor(field, myFixture.getProject(), refExpr, inlineThisOnly || element instanceof PsiCompiledElement).run();
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  private void doTestConflict(final String conflict) {
    try {
      doTest();
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals(conflict, e.getMessage());
    }
  }
}