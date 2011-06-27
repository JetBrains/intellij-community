package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;

/**
 * @author ven
 */
public class MockLocalToFieldHandler extends LocalToFieldHandler {
  private final boolean myMakeEnumConstant;
  public MockLocalToFieldHandler(Project project, boolean isConstant, final boolean makeEnumConstant) {
    super(project, isConstant);
    myMakeEnumConstant = makeEnumConstant;
  }

  @Override
  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences,
                                                                        boolean isStatic) {
    return new BaseExpressionToFieldHandler.Settings("xxx", null, occurences, true, isStatic, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                     PsiModifier.PRIVATE, local, local.getType(), false, aClass, true, myMakeEnumConstant);
  }
}
