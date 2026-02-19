// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.EvalInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * A binary operation that takes two types from the stack and returns
 * whether one is assignable from another
 */
public class IsAssignableInstruction extends EvalInstruction {
  public IsAssignableInstruction(PsiMethodCallExpression expression) {
    super(new JavaExpressionAnchor(expression), 2);
  }

  @Override
  public @NotNull DfaValue eval(@NotNull DfaValueFactory factory, @NotNull DfaMemoryState state, @NotNull DfaValue @NotNull ... arguments) {
    PsiType superClass = state.getDfType(arguments[1]).getConstantOfType(PsiType.class);
    PsiType subClass = state.getDfType(arguments[0]).getConstantOfType(PsiType.class);
    if (superClass != null && subClass != null) {
      TypeConstraint superType = TypeConstraints.instanceOf(superClass);
      TypeConstraint subType = TypeConstraints.instanceOf(subClass);
      if (subType.meet(superType) == TypeConstraints.BOTTOM) {
        return factory.fromDfType(DfTypes.FALSE);
      } else {
        TypeConstraint negated = subType.tryNegate();
        if (negated != null && negated.meet(superType) == TypeConstraints.BOTTOM) {
          return factory.fromDfType(DfTypes.TRUE);
        }
      }
    }
    return factory.getUnknown();
  }

  @Override
  public String toString() {
    return "IS_ASSIGNABLE_FROM";
  }
}
