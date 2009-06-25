/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 9, 2002
 * Time: 10:27:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;

public class TypeCastInstruction extends Instruction {
  private final PsiTypeCastExpression myCastExpression;
  private final PsiExpression myCasted;
  private final PsiType myCastTo;

  public TypeCastInstruction(PsiTypeCastExpression castExpression, PsiExpression casted, PsiType castTo) {
    myCastExpression = castExpression;
    myCasted = casted;
    myCastTo = castTo;
  }

  public PsiTypeCastExpression getCastExpression() {
    return myCastExpression;
  }
}
