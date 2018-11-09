// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.introduceVariable.InputValidator;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.refactoring.introduceVariable.IntroduceVariableSettings;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import org.jetbrains.annotations.NonNls;

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
  private final String myExpectedTypeCanonicalName;
  private final boolean myLookForType;

  MockIntroduceVariableHandler(@NonNls final String name, final boolean replaceAll,
                                      final boolean declareFinal, final boolean replaceLValues,
                                      @NonNls final String expectedTypeCanonicalName) {
    this(name, replaceAll, declareFinal, replaceLValues, expectedTypeCanonicalName, false);
  }

  MockIntroduceVariableHandler(@NonNls final String name, final boolean replaceAll,
                                      final boolean declareFinal, final boolean replaceLValues,
                                      @NonNls final String expectedTypeCanonicalName, boolean lookForType) {

    myName = name;
    myReplaceAll = replaceAll;
    myDeclareFinal = declareFinal;
    myReplaceLValues = replaceLValues;
    myExpectedTypeCanonicalName = expectedTypeCanonicalName;
    myLookForType = lookForType;
  }

  @Override
  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, final PsiExpression[] occurrences,
                                               TypeSelectorManagerImpl typeSelectorManager,
                                               final boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               InputValidator validator,
                                               PsiElement anchor, final JavaReplaceChoice replaceChoice) {
    final PsiType type = myLookForType ? findType(typeSelectorManager.getTypesForAll(), typeSelectorManager.getDefaultType())
                                       : typeSelectorManager.getDefaultType();
    assertEquals(type.getInternalCanonicalText(), myExpectedTypeCanonicalName);
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
    assertTrue(validationResult);
  }

  @Override
  protected void showErrorMessage(Project project, Editor editor, String message) {
    throw new RuntimeException("Error message:" + message);
  }

  private PsiType findType(final PsiType[] candidates, PsiType defaultType) {
    for (PsiType candidate : candidates) {
      if (candidate.equalsToText(myExpectedTypeCanonicalName)) return candidate;
    }
    return defaultType;
  }
}
