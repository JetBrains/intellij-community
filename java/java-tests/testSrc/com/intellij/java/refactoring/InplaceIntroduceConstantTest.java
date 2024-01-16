// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anna
 */
public class InplaceIntroduceConstantTest extends AbstractJavaInplaceIntroduceTest {
  private static final String BASE_PATH = "/refactoring/inplaceIntroduceConstant/";

  @Override
  protected void tearDown() throws Exception {
    try {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_REPLACE_ALL = false;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testReplaceAll() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  @Nullable
  @Override
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = super.getExpressionFromEditor();
    if (expression != null) {
      return expression;
    }
    final PsiExpression expr = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    return expr instanceof PsiLiteralExpression ? expr : null;
  }

  public void testReplaceAllInsideParenthesized() {
    doTest(introducer -> introducer.setReplaceAllOccurrences(true));
  }

  public void testReplaceAllWithClassRefType() {
    doTest(introducer -> {
     introducer.setReplaceAllOccurrences(true);
     type("ONE");
   });
  }

  public void testReplaceAllWithBrokenIdentifier() {
    doTest(introducer -> {
     type("A B");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testImplicitClass() {
    doTest(introducer -> {
     type("B");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testImplicitClassWithConflict() {
    doTest(introducer -> {
     type("a");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testReplaceAllFromSecondOccurrence() {
    doTest(introducer -> {
     type("O");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testReplaceAllFromLiteral() {
    doTest(introducer -> {
     type("NINE");
     introducer.setReplaceAllOccurrences(true);
   });
  }

  public void testConflictingConstantName() {
    doTest(null);
  }

  public void testConflictingConstantNameStaticallyImported() {
    doTest(null);
  }

  public void testNoConflictingConstantName() {
    doTest(null);
  }

  public void testEnsureVisibilityForAnno() {
    JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = PsiModifier.PRIVATE;
    try {
      doTest(null);
    }
    finally {
      JavaRefactoringSettings.getInstance().INTRODUCE_CONSTANT_VISIBILITY = null;
    }
  }

  public void testCorrectFinalPosition() {
    doTest(introducer -> type("SEC"));
  }

  public void testCorrectConstantPosition() {
    doTest(introducer -> type("R"));
  }

  public void testConstantBeforeUsage() {
    doTest(introducer -> type("R"));
  }

  public void testEscapePosition() {
    doTestEscape();
  }

  public void testEscapePositionOnLocal() {
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