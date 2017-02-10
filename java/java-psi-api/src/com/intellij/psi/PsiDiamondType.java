/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class PsiDiamondType extends PsiType {
  public static final RecursionGuard ourDiamondGuard = RecursionManager.createGuard("diamondInference");

  public PsiDiamondType() {
    super(TypeAnnotationProvider.EMPTY);
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

    public static final DiamondInferenceResult RAW_RESULT = new DiamondInferenceResult() {
      @NotNull
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return null;
      }
    };

    public static final DiamondInferenceResult UNRESOLVED_CONSTRUCTOR = new DiamondInferenceResult() {
      @NotNull
      @Override
      public PsiType[] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return "Cannot infer arguments (unable to resolve constructor)";
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

    public DiamondInferenceResult() { }

    public DiamondInferenceResult(String expressionPresentableText) {
      myNewExpressionPresentableText = expressionPresentableText;
    }

    @NotNull
    public PsiType[] getTypes() {
      return myErrorMessage == null ? myInferredTypes.toArray(createArray(myInferredTypes.size())) : PsiType.EMPTY_ARRAY;
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

    protected void addInferredType(PsiType psiType) {
      if (myErrorMessage != null) return;
      if (psiType == null) {
        myErrorMessage = "Cannot infer type arguments for " + myNewExpressionPresentableText;
      }
      else {
        myInferredTypes.add(psiType);
      }
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

  public static boolean hasDiamond(PsiNewExpression expression) {
    return getDiamondType(expression) != null;
  }

  public static PsiDiamondType getDiamondType(PsiNewExpression expression) {
    if (PsiUtil.isLanguageLevel7OrHigher(expression)) {
      final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
      if (classReference != null) {
        final PsiReferenceParameterList parameterList = classReference.getParameterList();
        if (parameterList != null) {
          final PsiTypeElement[] parameterElements = parameterList.getTypeParameterElements();
          if (parameterElements.length == 1) {
            final PsiType type = parameterElements[0].getType();
            return type instanceof PsiDiamondType ? (PsiDiamondType)type : null;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public abstract JavaResolveResult getStaticFactory();
}
