// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.JavaPsiEquivalenceUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementWeigher;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.AndFilter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.impl.search.MethodDeepestSuperSearcher;
import com.intellij.psi.scope.ElementClassFilter;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

class RecursionWeigher extends LookupElementWeigher {
  private final ElementFilter myFilter;
  private final PsiElement myPosition;
  private final PsiReferenceExpression myReference;
  @Nullable private final PsiMethodCallExpression myExpression;
  private final PsiMethod myPositionMethod;
  private final ExpectedTypeInfo[] myExpectedInfos;
  private final PsiExpression myCallQualifier;
  private final PsiExpression myPositionQualifier;
  private final boolean myDelegate;
  private final CompletionType myCompletionType;

  RecursionWeigher(PsiElement position,
                          CompletionType completionType,
                          @NotNull PsiReferenceExpression reference,
                          @Nullable PsiMethodCallExpression expression,
                          ExpectedTypeInfo[] expectedInfos) {
    super("recursion");
    myCompletionType = completionType;
    myFilter = recursionFilter(position);
    myPosition = position;
    myReference = reference;
    myExpression = expression;
    myPositionMethod = PsiTreeUtil.getParentOfType(position, PsiMethod.class, false);
    myExpectedInfos = expectedInfos;
    myCallQualifier = normalizeQualifier(myReference.getQualifierExpression());
    myPositionQualifier = normalizeQualifier(position.getParent() instanceof PsiJavaCodeReferenceElement
                                             ? ((PsiJavaCodeReferenceElement)position.getParent()).getQualifier()
                                             : null);
    myDelegate = isDelegatingCall();
  }

  @Nullable
  private static PsiExpression normalizeQualifier(@Nullable PsiElement qualifier) {
    return qualifier instanceof PsiThisExpression || !(qualifier instanceof PsiExpression) ? null : (PsiExpression)qualifier;
  }

  private boolean isDelegatingCall() {
    if (myCallQualifier != null &&
        myPositionQualifier != null &&
        myCallQualifier != myPositionQualifier &&
        JavaPsiEquivalenceUtil.areExpressionsEquivalent(myCallQualifier, myPositionQualifier)) {
      return false;
    }

    if (myCallQualifier == null && myPositionQualifier == null) {
      return false;
    }

    return true;
  }

  @Nullable
  static ElementFilter recursionFilter(PsiElement element) {
    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new ExcludeDeclaredFilter(ElementClassFilter.METHOD);
    }

    if (PsiJavaPatterns.psiElement().inside(
      StandardPatterns.or(
        PsiJavaPatterns.psiElement(PsiAssignmentExpression.class),
        PsiJavaPatterns.psiElement(PsiVariable.class))).
        andNot(PsiJavaPatterns.psiElement().afterLeaf(".")).accepts(element)) {
      return new AndFilter(new ExcludeSillyAssignment(),
                                                   new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class)));
    }
    return null;
  }

  private enum Result {
    delegation,
    normal,
    passingObjectToItself,
    recursive,
  }

  @NotNull
  @Override
  public Result weigh(@NotNull LookupElement element) {
    final Object object = element.getObject();
    if (!(object instanceof PsiMethod || object instanceof PsiVariable || object instanceof PsiExpression)) return Result.normal;

    if (myFilter != null && !myFilter.isAcceptable(object, myPosition)) {
      return Result.recursive;
    }

    if (isPassingObjectToItself(((PsiElement)object)) && myCompletionType == CompletionType.SMART) {
      return Result.passingObjectToItself;
    }

    if (myExpectedInfos != null) {
      final PsiType itemType = JavaCompletionUtil.getLookupElementType(element);
      if (itemType != null) {
        boolean hasRecursiveInvocations = false;
        boolean hasOtherInvocations = false;

        for (final ExpectedTypeInfo expectedInfo : myExpectedInfos) {
          PsiMethod calledMethod = expectedInfo.getCalledMethod();
          if (!expectedInfo.getType().isAssignableFrom(itemType)) continue;

          if (calledMethod != null && calledMethod.equals(myPositionMethod) || isGetterSetterAssignment(object, calledMethod)) {
            hasRecursiveInvocations = true;
          } else if (calledMethod != null) {
            hasOtherInvocations = true;
          }
        }
        if (hasRecursiveInvocations && !hasOtherInvocations) {
          return myDelegate ? Result.delegation : Result.recursive;
        }
      }
    }
    if (myExpression != null) {
      return Result.normal;
    }

    if (object instanceof PsiMethod method && myPositionMethod != null && PsiTreeUtil.isAncestor(myReference, myPosition, false) &&
        Objects.equals(method.getName(), myPositionMethod.getName())) {
      if (!myDelegate && findDeepestSuper(method).equals(findDeepestSuper(myPositionMethod))) {
        return Result.recursive;
      }
      return Result.delegation;
    }

    return Result.normal;
  }

  @Nullable
  private String getSetterPropertyName(@Nullable PsiMethod calledMethod) {
    if (PropertyUtilBase.isSimplePropertySetter(calledMethod)) {
      return PropertyUtilBase.getPropertyName(calledMethod);
    }
    PsiReferenceExpression reference = ExcludeSillyAssignment.getAssignedReference(myPosition);
    if (reference != null) {
      PsiElement target = reference.resolve();
      if (target instanceof PsiField) {
        return PropertyUtilBase.suggestPropertyName((PsiField)target);
      }
    }
    return null;
  }

  private boolean isGetterSetterAssignment(Object lookupObject, @Nullable PsiMethod calledMethod) {
    String prop = getSetterPropertyName(calledMethod);
    if (prop == null) return false;

    if (lookupObject instanceof PsiField &&
        prop.equals(PropertyUtilBase.suggestPropertyName((PsiField)lookupObject))) {
      return true;
    }
    if (lookupObject instanceof PsiMethod &&
        PropertyUtilBase.isSimplePropertyGetter((PsiMethod)lookupObject) &&
        prop.equals(PropertyUtilBase.getPropertyName((PsiMethod)lookupObject))) {
      return true;
    }
    return false;
  }

  private boolean isPassingObjectToItself(PsiElement element) {
    if (element instanceof PsiThisExpression) {
      return myCallQualifier != null && !myDelegate || myCallQualifier instanceof PsiSuperExpression;
    }
    return myCallQualifier instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)myCallQualifier).isReferenceTo(element);
  }

  @NotNull
  public static PsiMethod findDeepestSuper(@NotNull final PsiMethod method) {
    CommonProcessors.FindFirstProcessor<PsiMethod> processor = new CommonProcessors.FindFirstProcessor<>();
    MethodDeepestSuperSearcher.processDeepestSuperMethods(method, processor);
    final PsiMethod first = processor.getFoundValue();
    return first == null ? method : first;
  }
}
