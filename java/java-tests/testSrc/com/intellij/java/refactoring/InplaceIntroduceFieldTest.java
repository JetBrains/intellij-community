// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestApplication
public class InplaceIntroduceFieldTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceField/";

  @Override
  protected boolean isJava21() {
    return true;
  }

  @Test
  public void testAnchor() {
    doTest(null);
  }

  @Test
  public void testArrayInitializer() {
    doTest(null);
  }

  @Test
  public void testAnchor1() {
    doTest(null);
  }

  @Test
  public void testBeforeAssignment() {
    doTest(null);
  }

  @Test
  public void testTemplateAdjustment() {
    doTest(null);
  }

  @Test
  public void testBeforeAssignmentReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Test
  public void testBeforeAssignmentReplaceAllCall() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Test
  public void testReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Test
  public void testRestoreNewExpression() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Test
  public void testLocalResource() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }
  
  @Test
  public void testNormalizeDeclarationWithDisabledFormatting() {
    doTest(null);
  }

  @Test
  public void testEscapePosition() {
    doTestEscape();
  }

  @Test
  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  @Test
  public void testExtractNearAnotherDeclaration() {
    doTest(null);
  }

  @Test
  public void testStatementsBeforeSuper() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }
  
  @Test
  public void testNoExternalTypeAnnotations() {
    doTest(null);
  }

  @Test
  public void testNoExternalTypeAnnotations2() {
    doTest(null);
  }

  @Test
  public void testVarUnknownType() {
    assertThatThrownBy(() -> doTest(null)).isInstanceOf(CommonRefactoringUtil.RefactoringErrorHintException.class)
      .hasMessageContaining("Cannot perform refactoring.\nVariable type is unknown");
  }

  @Test
  public void testVarUnknownType2() {
    assertThatThrownBy(() -> doTest(null)).isInstanceOf(CommonRefactoringUtil.RefactoringErrorHintException.class)
      .hasMessageContaining("Cannot perform refactoring.\nVariable type is unknown");
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceFieldHandler();
  }

  public static class MyIntroduceFieldHandler extends IntroduceFieldHandler implements MyIntroduceHandler {
    @Override
    public boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor) {
      return super.invokeImpl(project, selectedExpr, editor);
    }

    @Override
    public boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor) {
      return super.invokeImpl(project, localVariable, editor);
    }
  }
}