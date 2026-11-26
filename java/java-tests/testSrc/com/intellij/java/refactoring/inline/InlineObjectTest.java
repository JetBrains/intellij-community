// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.inline;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inline.InlineObjectProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class InlineObjectTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final LightProjectDescriptor SRC_DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.HIGHEST, () -> IdeaTestUtil.getMockJdk17());
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, testName -> testName.contains("Src") ? SRC_DESCRIPTOR : DESCRIPTOR, "/java/java-tests/testData");

  @Test
  public void testInlinePoint() { doTest(); }
  @Test
  public void testInlinePointToString() { doTest(); }
  @Test
  public void testInlineBitString() { doTest(); }
  @Test
  public void testInlineSideEffect() { doTest(); }
  @Test
  public void testInlineConstructorWithStaticConstant() { doTest(); }
  
  @Test
  public void testRecordWithCompactConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "l_";
    settings.LOCAL_VARIABLE_NAME_SUFFIX = "_v";
    settings.PARAMETER_NAME_PREFIX = "p_";
    settings.PARAMETER_NAME_SUFFIX = "_r";
    doTest(); 
  }
  
  @Test
  public void testRecordWithCanonicalConstructor() { doTest(); }
  @Test
  public void testInlineFileParentSrc() {
    BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(this::doTest);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = configure();
    myFixture.runInCommand(() -> {
      performAction();
      myFixture.checkResultByFile(fileName + ".after");
    });
  }

  @NotNull
  private String configure() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    @NonNls String fileName = "/refactoring/inlineObject/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(fileName);
    return fileName;
  }

  private void performAction() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertInstanceOf(PsiJavaCodeReferenceElement.class, ref);
    final PsiElement parent = ((PsiJavaCodeReferenceElement)ref).getParent();
    assertInstanceOf(PsiNewExpression.class, parent);
    PsiMethod method = ((PsiNewExpression)parent).resolveConstructor();
    method = (PsiMethod)method.getNavigationElement();
    InlineObjectProcessor processor = InlineObjectProcessor.create(ref, method);
    assertNotNull(processor);
    processor.run();
  }
}
