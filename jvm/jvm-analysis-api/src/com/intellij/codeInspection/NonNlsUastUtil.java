// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

//TODO it is not intuitive that for Kotlin properties user should use @get:NonNls

/**
 * @see org.jetbrains.annotations.NonNls
 */
public final class NonNlsUastUtil {
  //TODO caching? (maybe at least for packages)

  /**
   * @return <code>true</code> if passed element is annotated with {@link org.jetbrains.annotations.NonNls}; <code>false</code> otherwise.
   */
  public static boolean isNonNlsAnnotated(@Nullable UElement element) {
    return element instanceof UAnnotated && ((UAnnotated)element).findAnnotation(AnnotationUtil.NON_NLS) != null;
  }

  /**
   * @return <code>true</code> if expression receiver should be considered non-localizable; <code>false</code> otherwise.
   */
  public static boolean isCallExpressionWithNonNlsReceiver(@Nullable UCallExpression expression) {
    if (expression == null) return false;
    UExpression receiver = expression.getReceiver();
    if (receiver == null) return false;

    if (receiver instanceof ULiteralExpression) {
      return isNonNlsStringLiteral((ULiteralExpression)receiver);
    }
    if (receiver instanceof USimpleNameReferenceExpression) { // reference to variable/field/parameter
      return isReferenceToNonNlsElement((USimpleNameReferenceExpression)receiver);
    }

    UElement expressionParent = expression.getUastParent();
    if (expressionParent instanceof UVariable && isNonNlsAnnotated(expressionParent)) { // initialized field/variable
      return true;
    }
    //TODO other cases?
    return false;
  }

  /**
   * @return <code>true</code> if passed string literal should be considered non-localizable; <code>false</code> otherwise.
   */
  public static boolean isNonNlsStringLiteral(@Nullable ULiteralExpression expression) {
    if (expression == null) return false;
    if (!UastLiteralUtils.isStringLiteral(expression)) return false;
    if (isPlacedInNonNlsClass(expression) || isPlacedInNonNlsPackage(expression)) return true;

    UElement parent = expression.getUastParent();
    if (parent instanceof UPolyadicExpression && !(parent instanceof UBinaryExpression)) { // multiline string literals
      parent = parent.getUastParent();
    }

    if (parent instanceof UField || parent instanceof ULocalVariable || parent instanceof UParameter) {
      return isNonNlsAnnotated(parent);
    }
    else if (parent instanceof UBinaryExpression) { // e.g. assigning value to parameter
      UExpression leftOperand = ((UBinaryExpression)parent).getLeftOperand();
      if (leftOperand instanceof USimpleNameReferenceExpression) {
        return isReferenceToNonNlsElement((USimpleNameReferenceExpression)leftOperand);
      }
    }
    else if (parent instanceof UReturnExpression) {
      return isReturnExpressionInNonNlsMethod((UReturnExpression)parent);
    }
    else if (parent instanceof UCallExpression) {
      return isNonNlsArgument(expression, (UCallExpression)parent) ||
             isCallExpressionWithNonNlsReceiver((UCallExpression)parent);
    }
    return false;
  }


  private static boolean isPlacedInNonNlsClass(@NotNull UElement element) {
    UClass containingClass = UastUtils.getContainingUClass(element);
    if (containingClass == null) return false;
    if (isNonNlsAnnotated(containingClass)) return true;

    //TODO potential performance issue here, cache classes?
    UClass superClass = containingClass;
    while ((superClass = superClass.getSuperClass()) != null) {
      if (isNonNlsAnnotated(superClass)) return true;
    }
    return false;
  }

  private static boolean isPlacedInNonNlsPackage(@NotNull UElement element) {
    return false; //TODO implement
  }

  private static boolean isReferenceToNonNlsElement(@NotNull USimpleNameReferenceExpression expression) {
    PsiElement resolved = expression.resolve();
    if (resolved == null) return false;
    UElement resolvedUElement = UastContextKt.toUElement(resolved);
    return isNonNlsAnnotated(resolvedUElement); //TODO does this cover all the cases?
  }

  private static boolean isReturnExpressionInNonNlsMethod(@NotNull UReturnExpression expression) {
    //TODO possible bugs (lambdas? anonymous classes in @NonNls methods?)
    UElement method = UastUtils.getParentOfType(expression, UMethod.class);
    return isNonNlsAnnotated(method);
  }

  //TODO support named parameters
  private static boolean isNonNlsArgument(@NotNull ULiteralExpression argument, @NotNull UCallExpression callExpression) {
    PsiParameter parameter = UastUtils.getParameterForArgument(callExpression, argument);
    if (parameter == null) return false;
    if (AnnotationUtil.findAnnotation(parameter, AnnotationUtil.NON_NLS) != null) return true;

    // special handling for equals()
    if (!HardcodedMethodConstants.EQUALS.equals(callExpression.getMethodName())) return false;
    PsiMethod method = callExpression.resolve();
    if (!MethodUtils.isEquals(method)) return false;
    return isCallExpressionWithNonNlsReceiver(callExpression);
  }
}
