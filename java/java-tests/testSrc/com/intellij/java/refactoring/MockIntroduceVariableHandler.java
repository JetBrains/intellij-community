// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *  @author dsl
 */
class MockIntroduceVariableHandler extends IntroduceVariableBase {
  private final String myName;
  private final boolean myReplaceAll;
  private final boolean myDeclareFinal;
  private final boolean myReplaceLValues;
  private final String myExpectedTypeText;
  private final boolean myLookForType;

  MockIntroduceVariableHandler(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText) {
    this(name, replaceAll, declareFinal, replaceLValues, expectedTypeText, false);
  }

  MockIntroduceVariableHandler(String name, boolean replaceAll, boolean declareFinal, boolean replaceLValues, String expectedTypeText, boolean lookForType) {
    myName = name;
    myReplaceAll = replaceAll;
    myDeclareFinal = declareFinal;
    myReplaceLValues = replaceLValues;
    myExpectedTypeText = expectedTypeText;
    myLookForType = lookForType;
  }

  @Override
  public IntroduceVariableSettings getSettings(Project project,
                                               Editor editor,
                                               PsiExpression expr,
                                               PsiExpression[] occurrences,
                                               TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               InputValidator validator,
                                               PsiElement anchor,
                                               JavaReplaceChoice replaceChoice) {
    PsiType defaultType = typeSelectorManager.getDefaultType();
    PsiType type = myLookForType ? findType(typeSelectorManager.getTypesForAll(), defaultType) : defaultType;
    assertEquals(myExpectedTypeText, type.getInternalCanonicalText());
    boolean isDeclareVarType = canBeExtractedWithoutExplicitType(expr) && createVarType();
    IntroduceVariableSettings introduceVariableSettings = new IntroduceVariableSettings() {
      @Override
      public @NlsSafe String getEnteredName() {
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

      @Override
      public boolean isDeclareVarType() {
        return isDeclareVarType;
      }
    };
    boolean validationResult = validator.isOK(introduceVariableSettings);
    assertValidationResult(validationResult);
    return introduceVariableSettings;
  }

  protected void assertValidationResult(boolean validationResult) {
    assertTrue(validationResult);
  }

  @Override
  protected void showErrorMessage(Project project, Editor editor, String message) {
    throw new RuntimeException("Error message:" + message);
  }

  private PsiType findType(final PsiType[] candidates, PsiType defaultType) {
    for (PsiType candidate : candidates) {
      if (candidate.equalsToText(myExpectedTypeText)) return candidate;
    }
    return defaultType;
  }
}