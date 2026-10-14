// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * @author anna
 */
@TestApplication
public class InplaceIntroduceConstantTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceConstant/";

  @AfterEach
  void tearDown() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_REPLACE_ALL = false;
  }

  @Test
  public void testReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Override
  protected @Nullable PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = super.getExpressionFromEditor();
    if (expression != null) {
      return expression;
    }
    final PsiExpression expr = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    return expr instanceof PsiLiteralExpression ? expr : null;
  }

  @Test
  public void testReplaceAllInsideParenthesized() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Test
  public void testReplaceAllWithClassRefType() {
    doTest(introducer -> {
     introducer.setReplaceAllOccurrences(true);
     type("ONE");
   });
  }

  @Test
  public void testReplaceAllWithBrokenIdentifier() {
    doTest(introducer -> {
     type("A B");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  @Test
  public void testImplicitClass() {
    doTest(introducer -> {
     type("B");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  @Test
  public void testImplicitClassWithConflict() {
    doTest(introducer -> {
     type("a");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  @Test
  public void testReplaceAllFromSecondOccurrence() {
    doTest(introducer -> {
     type("O");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  @Test
  public void testReplaceAllFromLiteral() {
    doTest(introducer -> {
     type("NINE");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  @Test
  public void testConflictingConstantName() {
    doTest(null);
  }

  @Test
  public void testConflictingConstantNameStaticallyImported() {
    doTest(null);
  }

  @Test
  public void testNoConflictingConstantName() {
    doTest(null);
  }

  @Test
  public void testEnsureVisibilityForAnno() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = PsiModifier.PRIVATE;
    try {
      doTest(null);
    }
    finally {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = null;
    }
  }

  @Test
  public void testCorrectFinalPosition() {
    doTest(_ -> type("SEC"));
  }

  @Test
  public void testCorrectConstantPosition() {
    doTest(_ -> type("R"));
  }

  @Test
  public void testConstantBeforeUsage() {
    doTest(_ -> type("R"));
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
  public void testCleanUpImport() {
    doTestEscape();
  }

  @Override
  protected String getBasePath() {
    return BASE_PATH;
  }

  @Override
  protected MyIntroduceHandler createIntroduceHandler() {
    return new MyIntroduceConstantHandler();
  }

  public static class MyIntroduceConstantHandler extends IntroduceConstantHandler implements MyIntroduceHandler {
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