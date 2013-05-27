/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  public void setTrueReachable() {
    myIsTrueReachable = true;
  }

  public void setFalseReachable() {
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

  protected void setPsiAnchor(PsiElement psiAnchor) {
    myExpression = psiAnchor;
    isConstTrue = psiAnchor != null && isBoolConst(psiAnchor);
  }
}
