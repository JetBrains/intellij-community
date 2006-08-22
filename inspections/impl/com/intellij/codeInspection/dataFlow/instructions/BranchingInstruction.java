/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 8, 2002
 * Time: 10:03:49 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NonNls;

public abstract class BranchingInstruction extends Instruction {
  private boolean myIsTrueReachable;
  private boolean myIsFalseReachable;
  private boolean isConstTrue;
  private PsiElement myExpression;

  protected BranchingInstruction() {
    myIsTrueReachable = false;
    myIsFalseReachable = false;
    setPsiAnchor(null);
  }

  public boolean isTrueReachable() {
    return myIsTrueReachable;
  }

  public boolean isFalseReachable() {
    return myIsFalseReachable;
  }

  public PsiElement getPsiAnchor() {
    return myExpression;
  }

  protected void setTrueReachable() {
    myIsTrueReachable = true;
  }

  protected void setFalseReachable() {
    myIsFalseReachable = true;
  }

  public boolean isConditionConst() {
    return !isConstTrue && myIsTrueReachable != myIsFalseReachable;
  }

  private static boolean isBoolConst(PsiElement condition) {
    if (!(condition instanceof PsiLiteralExpression)) return false;
    @NonNls String text = condition.getText();
    return "true".equals(text) || "false".equals(text);
  }

  protected void setPsiAnchor(PsiElement psiAcnchor) {
    myExpression = psiAcnchor;
    isConstTrue = psiAcnchor != null && isBoolConst(psiAcnchor);
  }
}
