package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;

/**
 * @author ven
 */
public class MockIntroduceFieldHandler extends IntroduceFieldHandler {
  private final InitializationPlace myInitializationPlace;
  private final boolean myDeclareStatic;

  public MockIntroduceFieldHandler(final InitializationPlace initializationPlace, final boolean declareStatic) {
    myInitializationPlace = initializationPlace;
    myDeclareStatic = declareStatic;
  }

  @Override
  protected Settings showRefactoringDialog(Project project, Editor editor, PsiClass parentClass, PsiExpression expr, PsiType type,
                                           PsiExpression[] occurrences, PsiElement anchorElement, PsiElement anchorElementIfAll) {
    final String fieldName = getNewName(project, expr, type);
    return new Settings(fieldName,  expr, occurrences, true, myDeclareStatic, true, myInitializationPlace,
            PsiModifier.PUBLIC,
            null,
            getFieldType(type), true, (TargetDestination)null, false, false);
  }

  protected String getNewName(Project project, PsiExpression expr, PsiType type) {
    SuggestedNameInfo name = JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.FIELD, null, expr, type);
    return name.names[0];
  }

  protected PsiType getFieldType(PsiType type) {
    return type;
  }
}
