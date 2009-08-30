/**
 * created at Sep 7, 2001
 * @author Jeka
 */
package com.intellij.refactoring.anonymousToInner;

import com.intellij.psi.*;

public class VariableInfo {
  public PsiVariable variable;
  public boolean saveInField = false;
  public boolean passAsParameter = true;
  public String parameterName;
  public String fieldName;

  public VariableInfo(PsiVariable variable) {
    this.variable = variable;
  }
}
