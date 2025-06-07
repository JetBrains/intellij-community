// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an indexed container (java.util.List or array)
 *
 * @author Tagir Valeev
 */
public abstract class IndexedContainer {
  private final @NotNull PsiExpression myQualifier;

  protected IndexedContainer(@NotNull PsiExpression qualifier) {
    myQualifier = qualifier;
  }

  /**
   * Returns true if the supplied method reference maps index to the collection element
   *
   * @param methodReference method reference to check
   * @return true if the supplied method reference is element retrieval method reference
   */
  public abstract boolean isGetMethodReference(PsiMethodReferenceExpression methodReference);

  /**
   * Returns an ancestor element retrieval expression if the supplied expression is the index used in it
   * (e.g. index in arr[index] or in list.get(index))
   *
   * @param indexExpression index expression
   * @return a surrounding element retrieval expression or null if no element retrieval expression found
   */
  public abstract PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression);

  /**
   * Extracts the element index if the supplied expression obtains the container element by index (either array[idx] or list.get(idx))
   *
   * @param expression expression to extract the index from
   * @return the extracted index or null if the supplied expression is not an element retrieval expression
   */
  public abstract PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression);

  /**
   * @return the qualifier of the expression which was used to create this {@code IndexedContainer}. The extracted qualifier might be
   * non-physical if it was implicit in the original code (e.g. "this" could be returned if original call was simply "size()")
   */
  public @NotNull PsiExpression getQualifier() {
    return myQualifier;
  }

  public boolean isQualifierEquivalent(@Nullable PsiExpression candidate) {
    candidate = PsiUtil.skipParenthesizedExprDown(candidate);
    return candidate != null && PsiEquivalenceUtil.areElementsEquivalent(myQualifier, candidate);
  }

  /**
   * @return type of the elements in the container or null if cannot be determined
   */
  public abstract PsiType getElementType();

  /**
   * Creates an IndexedContainer from length retrieval expression (like array.length or list.size())
   *
   * @param expression expression to create an IndexedContainer from
   * @return newly created IndexedContainer or null if the supplied expression is not length retrieval expression
   */
  public static @Nullable IndexedContainer fromLengthExpression(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    PsiExpression arrayExpression = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getArrayFromLengthExpression(expression));
    if (arrayExpression != null) {
      return new ArrayIndexedContainer(arrayExpression);
    }
    if (expression instanceof PsiMethodCallExpression call) {
      if (ListIndexedContainer.isSizeCall(call)) {
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(call.getMethodExpression()));
        if (qualifier != null) {
          return new ListIndexedContainer(qualifier);
        }
      }
    }
    return null;
  }

  /**
   * Use to create IndexedContainer for the next case:
   * <pre>{@code
   *  int[] newArray = new int[arrayLength];
   *  for (int i=0; i < arrayLength; i++) {
   *    newArray[i] = 0;
   *  }
   * }</pre>
   * Additionally, the method checks that newArray and arrayLength are not reassigned
   *
   * @param arrayAccessExpression expression to create an IndexedContainer from
   * @param bound                 reference to arrayLength
   * @return newly created IndexedContainer or null if it is impossible to resolve it
   */
  public static @Nullable IndexedContainer arrayContainerWithBound(@NotNull PsiArrayAccessExpression arrayAccessExpression,
                                                         @NotNull PsiExpression bound) {
    PsiExpression arrayExpression = arrayAccessExpression.getArrayExpression();
    if (!(arrayExpression instanceof PsiReferenceExpression reference) ||
        !(reference.resolve() instanceof PsiVariable arrayVariable)) {
      return null;
    }
    PsiExpression initializer = arrayVariable.getInitializer();
    if (!(initializer instanceof PsiNewExpression newExpression)) {
      return null;
    }
    PsiExpression[] dimensions = newExpression.getArrayDimensions();
    if (dimensions.length != 1) {
      return null;
    }
    PsiExpression dimension = dimensions[0];
    PsiVariable dimensionVariable = ExpressionUtils.resolveVariable(dimension);
    PsiVariable boundVariable = ExpressionUtils.resolveVariable(bound);
    if (dimensionVariable == null || boundVariable == null || !dimensionVariable.isEquivalentTo(boundVariable)) {
      return null;
    }
    if ((VariableAccessUtils.variableIsAssigned(dimensionVariable)) ||
        (VariableAccessUtils.variableIsAssigned(arrayVariable))) {
      return null;
    }
    return new ArrayIndexedContainer(arrayExpression);
  }

  static class ArrayIndexedContainer extends IndexedContainer {
    ArrayIndexedContainer(@NotNull PsiExpression qualifier) {
      super(qualifier);
    }

    @Override
    public boolean isGetMethodReference(PsiMethodReferenceExpression methodReference) {
      return false;
    }

    @Override
    public PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression) {
      if (indexExpression != null) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(indexExpression.getParent());
        if (parent instanceof PsiExpression &&
            PsiTreeUtil.isAncestor(extractIndexFromGetExpression((PsiExpression)parent), indexExpression, false)) {
          return (PsiExpression)parent;
        }
      }
      return null;
    }

    @Override
    public PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression) {
      expression = PsiUtil.skipParenthesizedExprDown(expression);
      if (expression instanceof PsiArrayAccessExpression arrayAccess) {
        if (isQualifierEquivalent(arrayAccess.getArrayExpression())) {
          return arrayAccess.getIndexExpression();
        }
      }
      return null;
    }

    @Override
    public PsiType getElementType() {
      PsiType type = getQualifier().getType();
      return type instanceof PsiArrayType ? ((PsiArrayType)type).getComponentType() : null;
    }
  }

  static class ListIndexedContainer extends IndexedContainer {
    ListIndexedContainer(@NotNull PsiExpression qualifier) {
      super(qualifier);
    }

    @Override
    public boolean isGetMethodReference(PsiMethodReferenceExpression methodReference) {
      if (!"get".equals(methodReference.getReferenceName())) return false;
      if (!isQualifierEquivalent(ExpressionUtils.getEffectiveQualifier(methodReference))) return false;
      PsiMethod method = ObjectUtils.tryCast(methodReference.resolve(), PsiMethod.class);
      return method != null && MethodUtils.methodMatches(method, CommonClassNames.JAVA_UTIL_LIST, null, "get", PsiTypes.intType());
    }

    @Override
    public PsiExpression extractGetExpressionFromIndex(@Nullable PsiExpression indexExpression) {
      if (indexExpression != null) {
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(indexExpression.getParent());
        if (parent instanceof PsiExpressionList) {
          PsiElement gParent = PsiUtil.skipParenthesizedExprUp(parent.getParent());
          if (gParent instanceof PsiMethodCallExpression &&
              PsiTreeUtil.isAncestor(extractIndexFromGetExpression((PsiExpression)gParent), indexExpression, false)) {
            return (PsiExpression)gParent;
          }
        }
      }
      return null;
    }

    @Override
    public PsiExpression extractIndexFromGetExpression(@Nullable PsiExpression expression) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(expression), PsiMethodCallExpression.class);
      if (call == null) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length == 1 && isGetCall(call) && isQualifierEquivalent(ExpressionUtils.getEffectiveQualifier(call.getMethodExpression()))) {
        return args[0];
      }
      return null;
    }

    @Override
    public PsiType getElementType() {
      PsiType type = PsiUtil.substituteTypeParameter(getQualifier().getType(), CommonClassNames.JAVA_UTIL_LIST, 0, false);
      return GenericsUtil.getVariableTypeByExpressionType(type);
    }

    static boolean isGetCall(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_UTIL_LIST, null, "get", PsiTypes.intType());
    }

    static boolean isSizeCall(PsiMethodCallExpression call) {
      return MethodCallUtils.isCallToMethod(call, CommonClassNames.JAVA_UTIL_LIST, PsiTypes.intType(), "size");
    }
  }
}
