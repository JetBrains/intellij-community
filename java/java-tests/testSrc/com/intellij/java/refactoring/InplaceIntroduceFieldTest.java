// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import org.jetbrains.annotations.NotNull;

public class InplaceIntroduceFieldTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceField/";

  public void testAnchor() {
    doTest(null);
  }

  public void testArrayInitializer() {
    doTest(null);
  }

  public void testAnchor1() {
    doTest(null);
  }

  public void testBeforeAssignment() {
    doTest(null);
  }

  public void testTemplateAdjustment() {
    doTest(null);
  }

  public void testBeforeAssignmentReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testBeforeAssignmentReplaceAllCall() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testRestoreNewExpression() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testLocalResource() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }
  
  public void testNormalizeDeclarationWithDisabledFormatting() {
    doTest(null);
  }

  public void testEscapePosition() {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() {
    doTestEscape();
  }

  public void testExtractNearAnotherDeclaration() {
    doTest(null);
  }

  public void testStatementsBeforeSuper() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
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