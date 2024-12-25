// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * A container for information about unhandled exceptions thrown from a block of code
 */
public class UnhandledExceptions {
  /**
   * An empty container: no exceptions are thrown
   */
  public static final UnhandledExceptions EMPTY = new UnhandledExceptions(Collections.emptySet(), false);

  /**
   * A container that assumes possible unknown exceptions from unresolved methods 
   * and no explicitly thrown exceptions
   */
  public static final UnhandledExceptions UNKNOWN = new UnhandledExceptions(Collections.emptySet(), true);

  private final Set<PsiClassType> exceptions;
  private final boolean hasUnresolvedCalls;

  private UnhandledExceptions(@NotNull Set<PsiClassType> exceptions, boolean hasUnresolvedCalls) {
    this.exceptions = exceptions;
    this.hasUnresolvedCalls = hasUnresolvedCalls;
  }

  /**
   * @return set of unhandled exception types thrown from a block of code
   */
  public Set<PsiClassType> exceptions() {
    return exceptions;
  }

  /**
   * @return true if the block of code has unresolved calls, which means that there are potentially unknown unhandled exceptions as well
   */
  public boolean hasUnresolvedCalls() {
    return hasUnresolvedCalls;
  }

  /**
   * @param exceptions         collection of exceptions; null is equivalent to empty
   * @param hasUnresolvedCalls if true, the block of code has unresolved calls
   * @return an {@code UnhandledExceptions} object; may reuse an existing object if possible
   */
  static @NotNull UnhandledExceptions from(@Nullable Collection<PsiClassType> exceptions, boolean hasUnresolvedCalls) {
    if (exceptions == null || exceptions.isEmpty()) {
      return hasUnresolvedCalls ? UNKNOWN : EMPTY;
    }
    return new UnhandledExceptions(exceptions instanceof Set ? (Set<PsiClassType>)exceptions :
                                   new HashSet<>(exceptions), hasUnresolvedCalls);
  }

  private @NotNull UnhandledExceptions withUnresolvedCalls(boolean unresolvedCalls) {
    return unresolvedCalls == this.hasUnresolvedCalls ? this : new UnhandledExceptions(exceptions, unresolvedCalls);
  }

  /**
   * @param other UnhandledExceptions container to merge with
   * @return merged container
   */
  public @NotNull UnhandledExceptions merge(@NotNull UnhandledExceptions other) {
    boolean unresolvedCalls = hasUnresolvedCalls || other.hasUnresolvedCalls;
    if (exceptions.isEmpty()) return other.withUnresolvedCalls(unresolvedCalls);
    if (other.exceptions.isEmpty()) return this.withUnresolvedCalls(unresolvedCalls);
    return new UnhandledExceptions(ContainerUtil.union(exceptions, other.exceptions), unresolvedCalls);
  }

  static @NotNull UnhandledExceptions collect(@NotNull PsiElement element,
                                              @Nullable PsiElement topElement,
                                              @NotNull Predicate<? super PsiCall> callFilter) {
    Collection<PsiClassType> unhandledExceptions = null;
    boolean hasUnresolvedCalls = false;
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      unhandledExceptions = ExceptionUtil.getUnhandledExceptions(expression, topElement, callFilter);
      if (!MethodCandidateInfo.isOverloadCheck() && 
          expression.resolveMethodGenerics() == JavaResolveResult.EMPTY && !isNonMethodNewExpression(expression)) {
        hasUnresolvedCalls = true;
      }
    }
    else if (element instanceof PsiTemplateExpression) {
      unhandledExceptions = ExceptionUtil.getUnhandledProcessorExceptions((PsiTemplateExpression)element, topElement);
    }
    else if (element instanceof PsiMethodReferenceExpression) {
      PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)element).getQualifierExpression();
      return qualifierExpression != null ? collect(qualifierExpression, topElement, callFilter) : EMPTY;
    }
    else if (element instanceof PsiLambdaExpression) {
      return EMPTY;
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement statement = (PsiThrowStatement)element;
      unhandledExceptions = ExceptionUtil.getUnhandledExceptions(statement, topElement);
    }
    else if (element instanceof PsiCodeBlock &&
             element.getParent() instanceof PsiMethod &&
             ((PsiMethod)element.getParent()).isConstructor() &&
             JavaPsiConstructorUtil.findThisOrSuperCallInConstructor((PsiMethod)element.getParent()) == null) {
      // there is implicit parent constructor call
      final PsiMethod constructor = (PsiMethod)element.getParent();
      final PsiClass aClass = constructor.getContainingClass();
      final PsiClass superClass = aClass == null ? null : aClass.getSuperClass();
      final PsiMethod[] superConstructors = superClass == null ? PsiMethod.EMPTY_ARRAY : superClass.getConstructors();
      Set<PsiClassType> unhandled = new HashSet<>();
      for (PsiMethod superConstructor : superConstructors) {
        if (!superConstructor.hasModifierProperty(PsiModifier.PRIVATE) && superConstructor.getParameterList().isEmpty()) {
          final PsiClassType[] exceptionTypes = superConstructor.getThrowsList().getReferencedTypes();
          for (PsiClassType exceptionType : exceptionTypes) {
            if (!ExceptionUtil.isUncheckedException(exceptionType) &&
                ExceptionUtil.getHandlePlace(element, exceptionType, topElement) == ExceptionUtil.HandlePlace.UNHANDLED) {
              unhandled.add(exceptionType);
            }
          }
          break;
        }
      }

      // plus all exceptions thrown in instance class initializers
      if (aClass != null) {
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)) continue;
          UnhandledExceptions byInitializer = collect(initializer.getBody(), initializer, callFilter);
          for (PsiClassType thrown : byInitializer.exceptions()) {
            if (ExceptionUtil.getHandlePlace(constructor.getBody(), thrown, topElement) == ExceptionUtil.HandlePlace.UNHANDLED) {
              unhandled.add(thrown);
            }
          }
          hasUnresolvedCalls |= byInitializer.hasUnresolvedCalls();
        }
      }
      unhandledExceptions = unhandled;
    }
    else if (element instanceof PsiResourceListElement) {
      final List<PsiClassType> unhandled = ExceptionUtil.getUnhandledCloserExceptions((PsiResourceListElement)element, topElement);
      if (!unhandled.isEmpty()) {
        unhandledExceptions = new HashSet<>(unhandled);
      }
    }

    UnhandledExceptions exceptions = from(unhandledExceptions, hasUnresolvedCalls);

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      UnhandledExceptions foundInChild = collect(child, topElement, callFilter);
      exceptions = exceptions.merge(foundInChild);
    }

    return exceptions;
  }

  private static boolean isNonMethodNewExpression(PsiCallExpression expression) {
    if (!(expression instanceof PsiNewExpression)) return false;
    PsiNewExpression newExpression = (PsiNewExpression)expression;
    if (newExpression.isArrayCreation()) return true;
    // Default constructor call?
    PsiExpressionList list = newExpression.getArgumentList();
    if (list == null || !list.isEmpty()) return false;
    PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
    return reference != null && reference.resolve() instanceof PsiClass;
  }

  /**
   * @param method method to check
   * @return unhandled exception that should be declared as thrown from a given method 
   * (it's not checked whether they are actually declared)
   */
  public static UnhandledExceptions ofMethod(@NotNull PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return EMPTY;
    PsiClass containingClass = method.getContainingClass();
    UnhandledExceptions result = collect(body, method, false);
    if (method.isConstructor() && containingClass != null) {
      // there may be field initializer throwing exception
      // that exception must be caught in the constructor
      PsiField[] fields = containingClass.getFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
        PsiExpression initializer = field.getInitializer();
        if (initializer == null) continue;
        result = result.merge(collect(initializer, field, true));
      }
    }
    return result;
  }

  /**
   * @param element Java code element (e.g., expression, or code block)
   * @return the unhandled exceptions thrown from a given element
   */
  public static @NotNull UnhandledExceptions collect(@NotNull PsiElement element) {
    return collect(element, element, true);
  }

  static @NotNull UnhandledExceptions collect(@NotNull PsiElement element,
                                              @Nullable PsiElement topElement,
                                              boolean includeSelfCalls) {
    @NotNull Predicate<? super PsiCall> callFilter = includeSelfCalls
                                                     ? c -> false
                                                     : expression -> {
                                                       PsiMethod method = expression.resolveMethod();
                                                       if (method == null) return false;
                                                       return method == PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
                                                     };
    return collect(element, topElement, callFilter);
  }
}
