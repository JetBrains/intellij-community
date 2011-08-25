/*
 * User: anna
 * Date: 29-Oct-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;

public class MockIntroduceConstantHandler extends IntroduceConstantHandler{
  private final PsiClass myTargetClass;

  public MockIntroduceConstantHandler(final PsiClass targetClass) {
    myTargetClass = targetClass;
  }

  @Override
  protected Settings showRefactoringDialog(final Project project, final Editor editor, final PsiClass parentClass, final PsiExpression expr,
                                           final PsiType type, final PsiExpression[] occurrences, final PsiElement anchorElement,
                                           final PsiElement anchorElementIfAll) {
    return new Settings("xxx", expr, occurrences, true, true, true, InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, null, false,
                        myTargetClass != null ? myTargetClass : parentClass, false, false);
  }

  protected String getVisibility() {
    return PsiModifier.PUBLIC;
  }
}
