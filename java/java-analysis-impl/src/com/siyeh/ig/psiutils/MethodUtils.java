// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MethodUtils {

  private static final Set<String> CAN_IGNORE_RETURN_VALUE_ANNOTATIONS = Set.of(
    "org.assertj.core.util.CanIgnoreReturnValue", "com.google.errorprone.annotations.CanIgnoreReturnValue");

  private MethodUtils() {}

  public static boolean isInsideMethodBody(@NotNull PsiElement element, @Nullable PsiMethod method) {
    return method != null && PsiTreeUtil.isAncestor(method.getBody(), element, true);
  }

  public static boolean isCopyConstructor(@Nullable PsiMethod constructor) {
    if (constructor == null || !constructor.isConstructor()) {
      return false;
    }
    final PsiParameter[] parameters = constructor.getParameterList().getParameters();
    return parameters.length == 1 && constructor.getContainingClass() == PsiUtil.resolveClassInClassTypeOnly(parameters[0].getType());
  }

  @Contract("null -> false")
  public static boolean isComparatorCompare(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, CommonClassNames.JAVA_UTIL_COMPARATOR, PsiTypes.intType(), "compare", null, null);
  }

  @Contract("null -> false")
  public static boolean isCompareTo(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiTypes.intType(), HardcodedMethodConstants.COMPARE_TO, PsiTypes.nullType())
      && InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_LANG_COMPARABLE);
  }

  @Contract("null -> false")
  public static boolean isCompareToIgnoreCase(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, "java.lang.String", PsiTypes.intType(), "compareToIgnoreCase", stringType);
  }

  @Contract("null -> false")
  public static boolean isHashCode(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiTypes.intType(), HardcodedMethodConstants.HASH_CODE);
  }

  @Contract("null -> false")
  public static boolean isFinalize(@Nullable PsiMethod method) {
    return method != null && methodMatches(method, null, PsiTypes.voidType(), HardcodedMethodConstants.FINALIZE);
  }

  @Contract("null -> false")
  public static boolean isToString(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, null, stringType, HardcodedMethodConstants.TO_STRING);
  }

  @Contract("null -> false")
  public static boolean isEquals(@Nullable PsiMethod method) {
    if (method == null || !HardcodedMethodConstants.EQUALS.equals(method.getName())) return false;
    PsiParameterList parameterList = method.getParameterList();
    return parameterList.getParametersCount() == 1 &&
           PsiTypes.booleanType().equals(method.getReturnType()) &&
           TypeUtils.isJavaLangObject(Objects.requireNonNull(parameterList.getParameter(0)).getType());
  }

  @Contract("null -> false")
  public static boolean isEqualsIgnoreCase(@Nullable PsiMethod method) {
    if (method == null) {
      return false;
    }
    final PsiClassType stringType = TypeUtils.getStringType(method);
    return methodMatches(method, "java.lang.String", PsiTypes.booleanType(), HardcodedMethodConstants.EQUALS_IGNORE_CASE, stringType);
  }

  /**
   * @param method              the method to compare to.
   * @param containingClassName the name of the class which contiains the
   *                            method.
   * @param returnType          the return type, specify null if any type matches
   * @param methodNamePattern   the name the method should have
   * @param parameterTypes      the type of the parameters of the method, specify
   *                            null if any number and type of parameters match or an empty array
   *                            to match zero parameters.
   * @return true, if the specified method matches the specified constraints,
   *         false otherwise
   */
  public static boolean methodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @Nullable PsiType returnType,
    @Nullable Pattern methodNamePattern,
    PsiType @Nullable ... parameterTypes) {
    if (methodNamePattern != null) {
      final String name = method.getName();
      final Matcher matcher = methodNamePattern.matcher(name);
      if (!matcher.matches()) {
        return false;
      }
    }
    return methodMatches(method, containingClassName, returnType, parameterTypes);
  }

  /**
   * @param method              the method to compare to.
   * @param containingClassName the name of the class which contiains the
   *                            method.
   * @param returnType          the return type, specify null if any type matches
   * @param methodName          the name the method should have
   * @param parameterTypes      the type of the parameters of the method, specify
   *                            null if any number and type of parameters match or an empty array
   *                            to match zero parameters.
   * @return true, if the specified method matches the specified constraints,
   *         false otherwise
   */
  public static boolean methodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @Nullable PsiType returnType,
    @NonNls @Nullable String methodName,
    PsiType @Nullable ... parameterTypes) {
    final String name = method.getName();
    if (methodName != null && !methodName.equals(name)) {
      return false;
    }
    return methodMatches(method, containingClassName, returnType, parameterTypes);
  }

  private static boolean methodMatches(@NotNull PsiMethod method,
                                       @NonNls @Nullable String containingClassName,
                                       @Nullable PsiType returnType,
                                       PsiType @Nullable ... parameterTypes) {
    if (parameterTypes != null) {
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != parameterTypes.length) {
        return false;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        ProgressManager.checkCanceled();
        final PsiParameter parameter = parameters[i];
        final PsiType type = parameter.getType();
        final PsiType parameterType = parameterTypes[i];
        if (PsiTypes.nullType().equals(parameterType)) {
          continue;
        }
        if (parameterType != null && !EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(type, parameterType)) {
          return false;
        }
      }
    }
    if (returnType != null) {
      final PsiType methodReturnType = method.getReturnType();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().typesAreEquivalent(returnType, methodReturnType)) {
        return false;
      }
    }
    if (containingClassName != null) {
      final PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, containingClassName);
    }
    return true;
  }

  public static boolean simpleMethodMatches(
    @NotNull PsiMethod method,
    @NonNls @Nullable String containingClassName,
    @NonNls @Nullable String returnTypeString,
    @NonNls @Nullable String methodName,
    @NonNls String @Nullable ... parameterTypeStrings) {
    final Project project = method.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory factory = psiFacade.getElementFactory();
    try {
      if (parameterTypeStrings != null) {
        final PsiType[] parameterTypes = PsiType.createArray(parameterTypeStrings.length);
        for (int i = 0; i < parameterTypeStrings.length; i++) {
          ProgressManager.checkCanceled();
          final String parameterTypeString = parameterTypeStrings[i];
          parameterTypes[i] = factory.createTypeFromText(parameterTypeString, method);
        }
        if (returnTypeString != null) {
          final PsiType returnType = factory.createTypeFromText(returnTypeString, method);
          return methodMatches(method, containingClassName, returnType, methodName, parameterTypes);
        }
        else {
          return methodMatches(method, containingClassName, null, methodName, parameterTypes);
        }
      }
      else if (returnTypeString != null) {
        final PsiType returnType = factory.createTypeFromText(returnTypeString, method);
        return methodMatches(method, containingClassName, returnType, methodName);
      }
      else {
        return methodMatches(method, containingClassName, null, methodName);
      }
    }
    catch (IncorrectOperationException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean hasSuper(@NotNull PsiMethod method) {
    PsiAnnotation overrideAnnotation = method.getModifierList().findAnnotation(CommonClassNames.JAVA_LANG_OVERRIDE);
    return overrideAnnotation != null || getSuper(method) != null;
  }

  public static @Nullable PsiMethod getSuper(@NotNull PsiMethod method) {
    final MethodSignatureBackedByPsiMethod signature = getSuperMethodSignature(method);
    if (signature == null) {
      return null;
    }
    return signature.getMethod();
  }

  public static @Nullable MethodSignatureBackedByPsiMethod getSuperMethodSignature(@NotNull PsiMethod method) {
    if (method.isConstructor() || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      return null;
    }
    return SuperMethodsSearch.search(method, null, true, false).findFirst();
  }

  /**
   * This method can get very slow and use a lot of memory when invoked on a method that is overridden many times,
   * like for example any of the methods of the {@link Object} class.
   * This is because the underlying api currently calculates all inheritors eagerly.
   * Try to avoid calling it in such cases.
   */
  public static boolean isOverridden(@NotNull PsiMethod method) {
    return OverridingMethodsSearch.search(method, false).findFirst() != null;
  }

  public static boolean isOverriddenInHierarchy(@NotNull PsiMethod method, @NotNull PsiClass baseClass) {
    // previous implementation:
    // final Query<PsiMethod> search = OverridingMethodsSearch.search(method);
    //for (PsiMethod overridingMethod : search) {
    //    final PsiClass aClass = overridingMethod.getContainingClass();
    //    if (InheritanceUtil.isCorrectDescendant(aClass, baseClass, true)) {
    //        return true;
    //    }
    //}
    // was extremely slow and used an enormous amount of memory for clone()
    if (!PsiUtil.canBeOverridden(method) || baseClass instanceof PsiAnonymousClass || baseClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    final Query<PsiClass> search = ClassInheritorsSearch.search(baseClass, baseClass.getUseScope(), true, true, true);
    for (PsiClass inheritor : search.asIterable()) {
      final PsiMethod overridingMethod = inheritor.findMethodBySignature(method, false);
      if (overridingMethod != null) {
        return true;
      }
    }
    return false;
  }

  public static boolean isEmpty(PsiMethod method) {
    return ControlFlowUtils.isEmptyCodeBlock(method.getBody());
  }

  /**
   * Returns true if the method or constructor is trivial, i.e. does nothing of consequence. This is true when the method is empty, but
   * also when it is a constructor which only calls super, contains empty statements, "if (false)" statements or only returns a constant.
   *
   * @param method  the method to check
   * @param considerTrivialPredicate  predicate to consider further statements as trivial.
   * For example, a predicate which returns {@code true} on {@link PsiThrowStatement}s could be used here.
   */
  public static boolean isTrivial(PsiMethod method, @Nullable Predicate<? super PsiStatement> considerTrivialPredicate) {
    if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      return false;
    }
    return isTrivial(method.getBody(), considerTrivialPredicate);
  }

  public static boolean isTrivial(PsiMethod method) {
    return isTrivial(method, null);
  }

  public static boolean isTrivial(PsiClassInitializer initializer) {
    return isTrivial(initializer.getBody(), null);
  }

  private static boolean isTrivial(PsiCodeBlock codeBlock, @Nullable Predicate<? super PsiStatement> trivialPredicate) {
    if (codeBlock == null) {
      return true;
    }
    final PsiStatement[] statements = codeBlock.getStatements();
    for (PsiStatement statement : statements) {
      ProgressManager.checkCanceled();
      if (statement instanceof PsiEmptyStatement || trivialPredicate != null && trivialPredicate.test(statement)) {
        continue;
      }
      if (statement instanceof PsiReturnStatement returnStatement) {
        final PsiExpression returnValue = PsiUtil.skipParenthesizedExprDown(returnStatement.getReturnValue());
        if (returnValue != null && !(returnValue instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      else if (statement instanceof PsiIfStatement ifStatement) {
        final PsiExpression condition = ifStatement.getCondition();
        final Object result = ExpressionUtils.computeConstantExpression(condition);
        if (result == null || !result.equals(Boolean.FALSE)) {
          return false;
        }
      }
      else if (statement instanceof PsiExpressionStatement expressionStatement) {
        if (!JavaPsiConstructorUtil.isSuperConstructorCall(expressionStatement.getExpression())) {
          return false;
        }
      }
      else {
        return false;
      }
    }
    return true;
  }

  public static boolean isTrivial(@NotNull UMethod method, @Nullable Predicate<? super UExpression> trivialPredicate) {
    if (method.getJavaPsi().hasModifier(JvmModifier.NATIVE)) return false;
    return isTrivial(method.getUastBody(), trivialPredicate);
  }

  public static boolean isTrivial(@NotNull UClassInitializer initializer) {
    return isTrivial(initializer.getUastBody(), null);
  }

  private static boolean isTrivial(@Nullable UExpression bodyExpression, @Nullable Predicate<? super UExpression> trivialPredicate) {
    if (bodyExpression == null) return true;
    final List<UExpression> expressions;
    if (bodyExpression instanceof UBlockExpression) {
      expressions = ((UBlockExpression)bodyExpression).getExpressions();
    } else {
      expressions = List.of(bodyExpression);
    }
    if (expressions.isEmpty()) return true;
    for (UExpression expression : expressions) {
      ProgressManager.checkCanceled();
      if (expression instanceof UastEmptyExpression || trivialPredicate != null && trivialPredicate.test(expression)) continue;
      if (expression instanceof UReturnExpression returnExpression) {
        final UExpression returnedExpression = returnExpression.getReturnExpression();
        if (returnedExpression != null && !(UastUtils.skipParenthesizedExprDown(returnedExpression) instanceof ULiteralExpression)) {
          return false;
        }
      }
      else if (expression instanceof UIfExpression ifExpression) {
        final UExpression condition = ifExpression.getCondition();
        final Object result = condition.evaluate();
        if (result == null || !result.equals(Boolean.FALSE)) return false;
      }
      else if (expression instanceof UCallExpression) {
        final String methodName = ((UCallExpression)expression).getMethodName();
        if (methodName != null && !methodName.equals(JavaKeywords.SUPER) && !methodName.equals("<init>")) return false;
      }
      else return false;
    }
    return true;
  }

  public static boolean hasInThrows(@NotNull PsiMethod method, String @NotNull ... exceptions) {
    if (exceptions.length == 0) {
      throw new IllegalArgumentException("no exceptions specified");
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiJavaCodeReferenceElement[] references = throwsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement reference : references) {
      ProgressManager.checkCanceled();
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass aClass)) {
        continue;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (ArrayUtil.contains(qualifiedName, exceptions)) return true;
    }
    return false;
  }

  public static boolean isChainable(PsiMethod method) {
    if (method == null) {
      return false;
    }
    if (!InheritanceUtil.isInheritorOrSelf(method.getContainingClass(), PsiUtil.resolveClassInClassTypeOnly(method.getReturnType()), true)) {
      return false;
    }
    final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(method.getBody());
    if (!(lastStatement instanceof PsiReturnStatement returnStatement)) {
      return false;
    }
    final PsiExpression returnValue = returnStatement.getReturnValue();
    return returnValue instanceof PsiThisExpression;
  }

  public static boolean haveEquivalentModifierLists(PsiMethod method, PsiMethod superMethod) {
    final PsiModifierList list1 = method.getModifierList();
    final PsiModifierList list2 = superMethod.getModifierList();
    if (list1.hasModifierProperty(PsiModifier.STRICTFP) != list2.hasModifierProperty(PsiModifier.STRICTFP) ||
        list1.hasModifierProperty(PsiModifier.SYNCHRONIZED) != list2.hasModifierProperty(PsiModifier.SYNCHRONIZED) ||
        list1.hasModifierProperty(PsiModifier.PUBLIC) != list2.hasModifierProperty(PsiModifier.PUBLIC) ||
        list1.hasModifierProperty(PsiModifier.PROTECTED) != list2.hasModifierProperty(PsiModifier.PROTECTED) ||
        list1.hasModifierProperty(PsiModifier.FINAL) != list2.hasModifierProperty(PsiModifier.FINAL) ||
        list1.hasModifierProperty(PsiModifier.ABSTRACT) != list2.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }
    return AnnotationUtil.equal(list1.getAnnotations(), list2.getAnnotations());
  }

  /**
   * Find a specific method by base class method and known specific type of the object
   *
   * @param method a base class method
   * @param specificType a specific type (class type or intersection type)
   * @return more specific method, or base class method if more specific method cannot be found
   */
  public static @NotNull PsiMethod findSpecificMethod(@NotNull PsiMethod method, @Nullable PsiType specificType) {
    PsiClass qualifierClass = method.getContainingClass();
    if (qualifierClass == null) return method;
    if (specificType == null || specificType instanceof PsiArrayType) return method;
    StreamEx<PsiType> types;
    if (specificType instanceof PsiIntersectionType) {
      types = StreamEx.of(((PsiIntersectionType)specificType).getConjuncts());
    } else {
      types = StreamEx.of(specificType);
    }
    List<PsiMethod> methods = types.map(PsiUtil::resolveClassInClassTypeOnly)
      .nonNull()
      .without(qualifierClass)
      .distinct()
      .filter(specificClass -> InheritanceUtil.isInheritorOrSelf(specificClass, qualifierClass, true))
      .map(specificClass -> MethodSignatureUtil.findMethodBySuperMethod(specificClass, method, true))
      .nonNull()
      .distinct()
      .toList();
    if (methods.isEmpty()) return method;
    PsiMethod best = methods.get(0);
    for (PsiMethod realMethod : methods) {
      if (best.equals(realMethod)) continue;
      if (MethodSignatureUtil.isSuperMethod(best, realMethod)) {
        best = realMethod;
      } else if (!MethodSignatureUtil.isSuperMethod(realMethod, best)) {
        // Several real candidates: give up
        return method;
      }
    }
    return best;
  }

  /**
   * Returns true if the supplied method is a static factory method,
   * that is a static method which returns an instance of the containing class
   */
  public static boolean isFactoryMethod(PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
    return aClass != null && aClass.equals(method.getContainingClass());
  }

  /**
   * Returns true if the only thing the supplied method does, is call a different method
   * with the same name located in the same class, with the same number or more parameters.
   */
  public static boolean isConvenienceOverload(PsiMethod method) {
    final PsiType returnType = method.getReturnType();
    final PsiCodeBlock body = method.getBody();
    final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
    if (statement == null) {
      return false;
    }
    if (PsiTypes.voidType().equals(returnType)) {
      if (!(statement instanceof PsiExpressionStatement expressionStatement)) {
        return false;
      }
      final PsiExpression expression = expressionStatement.getExpression();
      return isCallToOverloadedMethod(expression, method);
    }
    else {
      if (!(statement instanceof PsiReturnStatement returnStatement)) {
        return false;
      }
      final PsiExpression returnValue = returnStatement.getReturnValue();
      return isCallToOverloadedMethod(returnValue, method);
    }
  }

  private static boolean isCallToOverloadedMethod(PsiExpression expression, PsiMethod method) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final String name = methodCallExpression.getMethodExpression().getReferenceName();
    if (!method.getName().equals(name)) {
      return false;
    }
    final PsiMethod calledMethod = methodCallExpression.resolveMethod();
    if (calledMethod == null || calledMethod.getParameterList().getParametersCount() < method.getParameterList().getParametersCount()) {
      return false;
    }
    return calledMethod.getContainingClass() == method.getContainingClass();
  }

  public static boolean hasCanIgnoreReturnValueAnnotation(@NotNull PsiMethod method, @Nullable PsiElement stopElement) {
    return findAnnotationInTree(method, stopElement, CAN_IGNORE_RETURN_VALUE_ANNOTATIONS) != null;
  }

  /**
   * Finds the specified annotations in the psi tree.
   * For example first trying a method, then the surrounding class, then the surrounding package.
   * When the element checked is a method or class, super methods and classes are also checked.
   * @param element  the element at which to start searching
   * @param stop  psi element to stop at, and not continue to surrounding elements
   * @param fqAnnotationNames  the fully qualified names of the annotations to find
   * @return the first annotation found, or null if no annotation was found.
   */
  public static @Nullable PsiAnnotation findAnnotationInTree(PsiElement element, @Nullable PsiElement stop, @NotNull Set<String> fqAnnotationNames) {
    while (element != null) {
      if (element == stop) {
        return null;
      }
      if (element instanceof PsiModifierListOwner modifierListOwner) {
        final PsiAnnotation annotation =
          AnnotationUtil.findAnnotationInHierarchy(modifierListOwner, fqAnnotationNames);
        if (annotation != null) {
          return annotation;
        }
      }

      if (element instanceof PsiClassOwner classOwner) {
        final String packageName = classOwner.getPackageName();
        final PsiPackage aPackage = JavaPsiFacade.getInstance(element.getProject()).findPackage(packageName);
        if (aPackage == null) {
          return null;
        }
        final PsiAnnotation annotation = AnnotationUtil.findAnnotation(aPackage, fqAnnotationNames);
        if(annotation != null) {
          // Check that annotation actually belongs to the same library/source root
          // which could be important in case of split-packages
          final VirtualFile annotationFile = PsiUtilCore.getVirtualFile(annotation);
          final VirtualFile currentFile = classOwner.getVirtualFile();
          if(annotationFile != null && currentFile != null) {
            final ProjectFileIndex projectFileIndex = ProjectFileIndex.getInstance(element.getProject());
            final VirtualFile annotationClassRoot = projectFileIndex.getClassRootForFile(annotationFile);
            final VirtualFile currentClassRoot = projectFileIndex.getClassRootForFile(currentFile);
            if (!Objects.equals(annotationClassRoot, currentClassRoot)) {
              return null;
            }
          }
        }
        return annotation;
      }

      element = element.getContext();
    }
    return null;
  }

  /**
   * @param element element to find a method from
   * @return a method if the original element is inside method header; null otherwise
   */
  public static @Nullable PsiMethod getJavaMethodFromHeader(@Nullable PsiElement element) {
    if (element == null) return null;
    if (element.getLanguage() != JavaLanguage.INSTANCE) return null;
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (psiMethod != null && (element == psiMethod || element == psiMethod.getNameIdentifier() ||
                                 PsiTreeUtil.isAncestor(psiMethod.getModifierList(), element, false) ||
                                 PsiTreeUtil.isAncestor(psiMethod.getParameterList(), element, false))) {
      return psiMethod;
    }
    return null;
  }
}
