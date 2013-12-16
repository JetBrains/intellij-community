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
import org.jetbrains.annotations.Nullable;

public abstract class BranchingInstruction extends Instruction {
  private boolean myIsTrueReachable;
  private boolean myIsFalseReachable;
  private final boolean isConstTrue;
  private final PsiElement myExpression;

  protected BranchingInstruction(@Nullable PsiElement psiAnchor) {
    myIsTrueReachable = false;
    myIsFalseReachable = false;
    myExpression = psiAnchor;
    isConstTrue = psiAnchor != null && isBoolConst(psiAnchor);
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

  public static boolean isBoolConst(PsiElement condition) {
    if (!(condition instanceof PsiLiteralExpression)) return false;
    @NonNls String text = condition.getText();
    return "true".equals(text) || "false".equals(text);
  }

}
