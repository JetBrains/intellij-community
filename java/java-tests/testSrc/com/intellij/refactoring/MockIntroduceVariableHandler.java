package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.util.containers.MultiMap;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

/**
 *  @author dsl
 */
class MockIntroduceVariableHandler extends IntroduceVariableBase {
  private final String myName;
  private final boolean myReplaceAll;
  private final boolean myDeclareFinal;
  private final boolean myReplaceLValues;
  private final String myExpectedTypeCanonicalName;

  public MockIntroduceVariableHandler(@NonNls final String name, final boolean replaceAll,
                                 final boolean declareFinal, final boolean replaceLValues,
                                 @NonNls final String expectedTypeCanonicalName) {

    myName = name;
    myReplaceAll = replaceAll;
    myDeclareFinal = declareFinal;
    myReplaceLValues = replaceLValues;
    myExpectedTypeCanonicalName = expectedTypeCanonicalName;
  }


  @Override
  protected IntroduceVariableSettings getSettings(Project project, Editor editor, PsiExpression expr, final PsiElement[] occurrences,
                                                  boolean anyAssignmentLHS, final boolean declareFinalIfAll, final PsiType type,
                                                  TypeSelectorManagerImpl typeSelectorManager, InputValidator validator) {
    Assert.assertTrue(type.getCanonicalText(), type.equalsToText(myExpectedTypeCanonicalName));
    IntroduceVariableSettings introduceVariableSettings = new IntroduceVariableSettings() {
      @Override
      public String getEnteredName() {
        return myName;
      }

      @Override
      public boolean isReplaceAllOccurrences() {
        return myReplaceAll && occurrences.length > 1;
      }

      @Override
      public boolean isDeclareFinal() {
        return myDeclareFinal || isReplaceAllOccurrences() && declareFinalIfAll;
      }

      @Override
      public boolean isReplaceLValues() {
        return myReplaceLValues;
      }

      @Override
      public PsiType getSelectedType() {
        return type;
      }

      @Override
      public boolean isOK() {
        return true;
      }
    };
    final boolean validationResult = validator.isOK(introduceVariableSettings);
    assertValidationResult(validationResult);
    return introduceVariableSettings;
  }

  protected void assertValidationResult(final boolean validationResult) {
    Assert.assertTrue(validationResult);
  }

  @Override
  protected void showErrorMessage(Project project, Editor editor, String message) {
    throw new RuntimeException("Error message:" + message);
  }

  @Override
  protected void highlightReplacedOccurences(final Project project, Editor editor, final PsiElement[] replacedOccurences) {

  }

  @Override
  protected boolean reportConflicts(final MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
    return false;
  }
}
