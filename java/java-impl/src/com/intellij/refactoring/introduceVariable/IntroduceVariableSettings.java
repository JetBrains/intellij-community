/**
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: Nov 15, 2002
 * Time: 4:12:48 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.refactoring.introduceVariable;

import com.intellij.psi.PsiType;

public interface IntroduceVariableSettings {
  String getEnteredName();

  boolean isReplaceAllOccurrences();

  boolean isDeclareFinal();

  boolean isReplaceLValues();

  PsiType getSelectedType();

  boolean isOK();
}
