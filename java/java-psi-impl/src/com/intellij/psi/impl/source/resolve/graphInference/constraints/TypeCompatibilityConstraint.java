/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.graphInference.constraints;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * User: anna
 */
public class TypeCompatibilityConstraint implements ConstraintFormula {
  private PsiType myT;
  private PsiType myS;

  public TypeCompatibilityConstraint(@NotNull PsiType t, @NotNull PsiType s) {
    myT = t;
    myS = s;
  }

  @Override
  public boolean reduce(InferenceSession session, List<ConstraintFormula> constraints) {
    if (session.isProperType(myT) && session.isProperType(myS)) {
      return TypeConversionUtil.isAssignable(myS, myT);
    }
    if (myS instanceof PsiPrimitiveType) {
      final PsiClassType boxedType = ((PsiPrimitiveType)myS).getBoxedType(session.getManager(), session.getScope());
      if (boxedType != null) {
        constraints.add(new TypeCompatibilityConstraint(myT, boxedType));
        return true;
      }
    }
    if (myT instanceof PsiPrimitiveType) {
      final PsiClassType boxedType = ((PsiPrimitiveType)myT).getBoxedType(session.getManager(), session.getScope());
      if (boxedType != null) {
        constraints.add(new TypeCompatibilityConstraint(boxedType, myS));
        return true;
      }
    }
    constraints.add(new SubtypingConstraint(myT, myS, true));
    return true;
  }

  @Override
  public void apply(PsiSubstitutor substitutor) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeCompatibilityConstraint that = (TypeCompatibilityConstraint)o;

    if (!myS.equals(that.myS)) return false;
    if (!myT.equals(that.myT)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myT.hashCode();
    result = 31 * result + myS.hashCode();
    return result;
  }
}
