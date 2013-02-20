/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class PsiDiamondType extends PsiType {
  public static final RecursionGuard ourDiamondGuard = RecursionManager.createGuard("diamondInference");

  public PsiDiamondType(PsiAnnotation[] annotations) {
    super(annotations);
  }

  public abstract DiamondInferenceResult resolveInferredTypes();

  public static class DiamondInferenceResult {
    public static final DiamondInferenceResult EXPLICIT_CONSTRUCTOR_TYPE_ARGS = new DiamondInferenceResult() {
      @NotNull
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot use diamonds with explicit type parameters for constructor";
      }
    };

    public static final DiamondInferenceResult NULL_RESULT = new DiamondInferenceResult() {
      @NotNull
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot infer arguments";
      }
    };

    public static final DiamondInferenceResult ANONYMOUS_INNER_RESULT = new DiamondInferenceResult() {
      @NotNull
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot use ''<>'' with anonymous inner classes";
      }
    };

    private final List<PsiType> myInferredTypes = new ArrayList<PsiType>();
    private String myErrorMessage;

    private String myNewExpressionPresentableText;
    private Project myProject;

    public DiamondInferenceResult() {
    }

    public DiamondInferenceResult(String expressionPresentableText, Project project) {
      myNewExpressionPresentableText = expressionPresentableText;
      myProject = project;
    }

    @NotNull
    public PsiType[] getTypes() {
      if (myErrorMessage != null) {
        return PsiType.EMPTY_ARRAY;
      }
      return myInferredTypes.toArray(new PsiType[myInferredTypes.size()]);
    }

    /**
     * @return all inferred types even if inference failed
     */
    public List<PsiType> getInferredTypes() {
      return myInferredTypes;
    }

    public String getErrorMessage() {
      return myErrorMessage;
    }

    public boolean failedToInfer() {
      return myErrorMessage != null;
    }

    public void addInferredType(PsiType psiType) {
      if (myErrorMessage != null) return;
      if (psiType == null) {
        myErrorMessage = "Cannot infer type arguments for " + myNewExpressionPresentableText;
      } /*else if (!isValid(psiType)) {
        myErrorMessage = "Cannot infer type arguments for " +
                         myNewExpressionPresentableText + " because type " + psiType.getPresentableText() + " inferred is not allowed in current context";
      }*/ else {
        myInferredTypes.add(psiType);
      }
    }

    private static Boolean isValid(PsiType type) {
      return type.accept(new PsiTypeVisitor<Boolean>() {
        @Override
        public Boolean visitType(PsiType type) {
          return !(type instanceof PsiIntersectionType);
        }

        @Override
        public Boolean visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
          return false;
        }

        @Override
        public Boolean visitWildcardType(PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            if (bound instanceof PsiIntersectionType) return false;
            return bound.accept(this);
          }
          return true;
        }

        @Override
        public Boolean visitClassType(PsiClassType classType) {
          for (PsiType psiType : classType.getParameters()) {
            if (!psiType.accept(this)) {
              return false;
            }
          }
          return true;
        }
      });
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DiamondInferenceResult that = (DiamondInferenceResult)o;

      if (myErrorMessage != null ? !myErrorMessage.equals(that.myErrorMessage) : that.myErrorMessage != null) return false;
      if (!myInferredTypes.equals(that.myInferredTypes)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myInferredTypes.hashCode();
      result = 31 * result + (myErrorMessage != null ? myErrorMessage.hashCode() : 0);
      return result;
    }
  }
}
