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

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceBound;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceVariable;
import com.intellij.psi.util.PsiUtil;

import java.util.List;

public class TypeEqualityConstraint implements ConstraintFormula {
  private static final Logger LOG = Logger.getInstance(TypeEqualityConstraint.class);
  private PsiType myT;
  private PsiType myS;

  public TypeEqualityConstraint(PsiType t, PsiType s) {
    myT = t;
    myS = s;
  }

  @Override
  public boolean reduce(InferenceSession session, List<? super ConstraintFormula> constraints) {
    if (myT instanceof PsiWildcardType && myS instanceof PsiWildcardType) {
      final PsiType tBound = ((PsiWildcardType)myT).getBound();
      final PsiType sBound = ((PsiWildcardType)myS).getBound();

      if (tBound == null && sBound == null) return true;

      if (sBound == null && ((PsiWildcardType)myT).isExtends()) {
        //extends bound of "?" (Object)
        constraints.add(new TypeEqualityConstraint(((PsiWildcardType)myS).getExtendsBound(), tBound));
        return true;
      }

      if (tBound == null && ((PsiWildcardType)myS).isExtends()) {
        //extends bound of "?" (Object)
        constraints.add(new TypeEqualityConstraint(((PsiWildcardType)myT).getExtendsBound(), sBound));
        return true;
      }

      if (((PsiWildcardType)myT).isExtends() && ((PsiWildcardType)myS).isExtends() ||
          ((PsiWildcardType)myT).isSuper() && ((PsiWildcardType)myS).isSuper()) {

        LOG.assertTrue(tBound != null);
        LOG.assertTrue(sBound != null);
        constraints.add(new TypeEqualityConstraint(tBound, sBound));
        return true;
      }
    }

    if (myT instanceof PsiWildcardType || myS instanceof PsiWildcardType) {
      session.registerIncompatibleErrorMessage(
        JavaPsiBundle.message("error.incompatible.type.incompatible.equality.constraint", session.getPresentableText(myT), session.getPresentableText(myS)));
      return false;
    }

    if (session.isProperType(myT) && session.isProperType(myS)) {
      final boolean equal = Comparing.equal(myT, myS);
      if (!equal) {
        session.registerIncompatibleErrorMessage(
          JavaPsiBundle.message("error.incompatible.type.incompatible.equality.constraint", session.getPresentableText(myT), session.getPresentableText(myS)));
      }
      return equal;
    }

    if (myT == null || myT == PsiTypes.nullType()) return false;
    if (myS == null || myS == PsiTypes.nullType()) return false;

    InferenceVariable inferenceVariable = session.getInferenceVariable(myS);
    if (inferenceVariable != null && !(myT instanceof PsiPrimitiveType)) {
      InferenceVariable.addBound(myS, myT, InferenceBound.EQ, session);
      return true;
    }
    inferenceVariable = session.getInferenceVariable(myT);
    if (inferenceVariable != null && !(myS instanceof PsiPrimitiveType)) {
      InferenceVariable.addBound(myT, myS, InferenceBound.EQ, session);
      return true;
    }
    if (myT instanceof PsiClassType && myS instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult tResult = ((PsiClassType)myT).resolveGenerics();
      final PsiClassType.ClassResolveResult sResult = ((PsiClassType)myS).resolveGenerics();
      final PsiClass tClass = tResult.getElement();
      //equal erasure
      if (tClass != null && tClass.getManager().areElementsEquivalent(tClass, sResult.getElement())) {
        final PsiSubstitutor tSubstitutor = tResult.getSubstitutor();
        final PsiSubstitutor sSubstitutor = sResult.getSubstitutor();
        for (PsiTypeParameter typeParameter : PsiUtil.typeParametersIterable(tClass)) {
          final PsiType tSubstituted = tSubstitutor.substitute(typeParameter);
          final PsiType sSubstituted = sSubstitutor.substitute(typeParameter);
          if (tSubstituted != null && sSubstituted != null) {
            constraints.add(new TypeEqualityConstraint(tSubstituted, sSubstituted));
          }
          if (tSubstituted == null ^ sSubstituted == null) {
            session.registerIncompatibleErrorMessage(
              JavaPsiBundle.message("error.incompatible.type.incompatible.equality.constraint", session.getPresentableText(myT), session.getPresentableText(myS)));
            return false;
          }
        }
        return true;
      }
    }
    if (myT instanceof PsiArrayType && myS instanceof PsiArrayType) {
      constraints.add(new TypeEqualityConstraint(((PsiArrayType)myT).getComponentType(), ((PsiArrayType)myS).getComponentType()));
      return true;
    }

    session.registerIncompatibleErrorMessage(session.getInferenceVariables(),
                                             JavaPsiBundle.message("type.conforms.to.constraint", session.getPresentableText(myS), session.getPresentableText(myT)));
    return false;
  }

  @Override
  public void apply(PsiSubstitutor substitutor, boolean cache) {
    myT = substitutor.substitute(myT);
    myS = substitutor.substitute(myS);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TypeEqualityConstraint that = (TypeEqualityConstraint)o;

    if (myS != null ? !myS.equals(that.myS) : that.myS != null) return false;
    if (myT != null ? !myT.equals(that.myT) : that.myT != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myT != null ? myT.hashCode() : 0;
    result = 31 * result + (myS != null ? myS.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return myT.getPresentableText() + " == " + myS.getPresentableText();
  }
}
