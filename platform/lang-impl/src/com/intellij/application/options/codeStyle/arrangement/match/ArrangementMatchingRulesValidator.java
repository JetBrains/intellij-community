// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementMatchingRulesValidator {
  protected ArrangementMatchingRulesModel myRulesModel;


  public ArrangementMatchingRulesValidator(ArrangementMatchingRulesModel model) {
    myRulesModel = model;
  }

  protected @Nullable @Nls String validate(int index) {
    if (myRulesModel.getSize() < index) {
      return null;
    }

    final Object target = myRulesModel.getElementAt(index);
    if (target instanceof StdArrangementMatchRule) {
      for (int i = 0; i < index; i++) {
        final Object element = myRulesModel.getElementAt(i);
        if (element instanceof StdArrangementMatchRule && target.equals(element)) {
          return ApplicationBundle.message("arrangement.settings.validation.duplicate.matching.rule");
        }
      }
    }
    return null;
  }
}
