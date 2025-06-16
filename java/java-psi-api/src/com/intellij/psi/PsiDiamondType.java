// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.core.JavaPsiBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class PsiDiamondType extends PsiType {
  public static final RecursionGuard<PsiElement> ourDiamondGuard = RecursionManager.createGuard("diamondInference");

  public PsiDiamondType() {
    super(TypeAnnotationProvider.EMPTY);
  }

  public abstract DiamondInferenceResult resolveInferredTypes();

  public static class DiamondInferenceResult {
    public static final DiamondInferenceResult EXPLICIT_CONSTRUCTOR_TYPE_ARGS = new DiamondInferenceResult() {
      @Override
      public PsiType @NotNull [] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return JavaPsiBundle.message("diamond.error.explicit.type.parameters.for.constructor");
      }
    };

    public static final DiamondInferenceResult NULL_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType @NotNull [] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return JavaPsiBundle.message("diamond.error.cannot.infer.arguments");
      }
    };

    public static final DiamondInferenceResult RAW_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType @NotNull [] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return null;
      }
    };

    public static final DiamondInferenceResult UNRESOLVED_CONSTRUCTOR = new DiamondInferenceResult() {
      @Override
      public PsiType @NotNull [] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return JavaPsiBundle.message("diamond.error.cannot.infer.arguments.unable.to.resolve.constructor");
      }
    };

    public static final DiamondInferenceResult ANONYMOUS_INNER_RESULT = new DiamondInferenceResult() {
      @Override
      public PsiType @NotNull [] getTypes() {
        return PsiType.EMPTY_ARRAY;
      }

      @Override
      public String getErrorMessage() {
        return JavaPsiBundle.message("diamond.error.anonymous.inner.classes");
      }
    };

    private final List<PsiType> myInferredTypes = new ArrayList<>();
    private @NlsContexts.DetailedDescription String myErrorMessage;
    private String myNewExpressionPresentableText;

    public DiamondInferenceResult() { }

    public DiamondInferenceResult(String expressionPresentableText) {
      myNewExpressionPresentableText = expressionPresentableText;
    }

    public PsiType @NotNull [] getTypes() {
      return myErrorMessage == null ? myInferredTypes.toArray(createArray(myInferredTypes.size())) : PsiType.EMPTY_ARRAY;
    }

    /**
     * @return all inferred types even if inference failed
     */
    public List<PsiType> getInferredTypes() {
      return myInferredTypes;
    }

    public @NlsContexts.DetailedDescription String getErrorMessage() {
      return myErrorMessage;
    }

    public boolean failedToInfer() {
      return myErrorMessage != null;
    }

    @ApiStatus.Internal
    public void addInferredType(PsiType psiType) {
      if (myErrorMessage != null) return;
      if (psiType == null) {
        myErrorMessage = JavaPsiBundle.message("diamond.error.cannot.infer.type.arguments", myNewExpressionPresentableText);
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
    if (PsiUtil.isAvailable(JavaFeature.DIAMOND_TYPES, expression)) {
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

  public static JavaResolveResult getDiamondsAwareResolveResult(PsiCall expression) {
    if (expression instanceof PsiNewExpression) {
      PsiDiamondType diamondType = getDiamondType((PsiNewExpression)expression);
      if (diamondType != null) {
        JavaResolveResult factory = diamondType.getStaticFactory();
        return factory != null ? factory : JavaResolveResult.EMPTY;
      }
    }
    
    if (expression instanceof PsiEnumConstant) {
      final PsiEnumConstant enumConstant = (PsiEnumConstant)expression;
      PsiClass containingClass = enumConstant.getContainingClass();
      if (containingClass == null) return JavaResolveResult.EMPTY;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(enumConstant.getProject());
      final PsiClassType type = facade.getElementFactory().createType(containingClass);
      PsiExpressionList argumentList = enumConstant.getArgumentList();
      if (argumentList == null) return JavaResolveResult.EMPTY;
      return facade.getResolveHelper().resolveConstructor(type, argumentList, enumConstant);
    }

    return expression.resolveMethodGenerics();
  }

  public abstract @Nullable JavaResolveResult getStaticFactory();

  /**
   * @return array of potentially applicable static factories
   */
  public abstract JavaResolveResult @NotNull[] getStaticFactories();
}
