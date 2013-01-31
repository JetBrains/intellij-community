/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author mike
 */
public class ExceptionUtil {
  @NonNls private static final String CLONE_METHOD_NAME = "clone";

  private ExceptionUtil() {}

  @NotNull
  public static List<PsiClassType> getThrownExceptions(@NotNull PsiElement[] elements) {
    List<PsiClassType> array = ContainerUtil.newArrayList();
    for (PsiElement element : elements) {
      List<PsiClassType> exceptions = getThrownExceptions(element);
      addExceptions(array, exceptions);
    }

    return array;
  }

  @NotNull
  public static List<PsiClassType> getThrownCheckedExceptions(@NotNull PsiElement[] elements) {
    List<PsiClassType> exceptions = getThrownExceptions(elements);
    if (exceptions.isEmpty()) return exceptions;
    exceptions = filterOutUncheckedExceptions(exceptions);
    return exceptions;
  }

  @NotNull
  private static List<PsiClassType> filterOutUncheckedExceptions(List<PsiClassType> exceptions) {
    List<PsiClassType> array = ContainerUtil.newArrayList();
    for (PsiClassType exception : exceptions) {
      if (!isUncheckedException(exception)) array.add(exception);
    }
    return array;
  }

  @NotNull
  public static List<PsiClassType> getThrownExceptions(@NotNull PsiElement element) {
    if (element instanceof PsiClass) {
      if (element instanceof PsiAnonymousClass) {
        final PsiExpressionList argumentList = ((PsiAnonymousClass)element).getArgumentList();
        if (argumentList != null){
          return getThrownExceptions(argumentList);
        }
      }
      // filter class declaration in code
      return Collections.emptyList();
    }
    else if (element instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodRef = ((PsiMethodCallExpression)element).getMethodExpression();
      JavaResolveResult result = methodRef.advancedResolve(false);
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiNewExpression) {
      JavaResolveResult result = ((PsiNewExpression)element).resolveMethodGenerics();
      return getExceptionsByMethodAndChildren(element, result);
    }
    else if (element instanceof PsiThrowStatement) {
      final PsiExpression expr = ((PsiThrowStatement)element).getException();
      if (expr == null) return Collections.emptyList();
      final List<PsiType> types = getPreciseThrowTypes(expr);
      final List<PsiClassType> classTypes = ContainerUtil.mapNotNull(types, new NullableFunction<PsiType, PsiClassType>() {
        @Override public PsiClassType fun(PsiType type) { return type instanceof PsiClassType ? (PsiClassType)type : null; }
      });
      addExceptions(classTypes, getThrownExceptions(expr));
      return classTypes;
    }
    else if (element instanceof PsiTryStatement) {
      return getTryExceptions((PsiTryStatement)element);
    }
    else if (element instanceof PsiResourceVariable) {
      final PsiResourceVariable variable = (PsiResourceVariable)element;
      final List<PsiClassType> types = ContainerUtil.newArrayList();
      addExceptions(types, getCloserExceptions(variable));
      final PsiExpression initializer = variable.getInitializer();
      if (initializer != null) addExceptions(types, getThrownExceptions(initializer));
      return types;
    }
    return getThrownExceptions(element.getChildren());
  }

  private static List<PsiClassType> getTryExceptions(PsiTryStatement tryStatement) {
    List<PsiClassType> array = ContainerUtil.newArrayList();

    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceVariable variable : resourceList.getResourceVariables()) {
        addExceptions(array, getUnhandledCloserExceptions(variable, resourceList));
      }
    }

    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock != null) {
      addExceptions(array, getThrownExceptions(tryBlock));
    }

    for (PsiParameter parameter : tryStatement.getCatchBlockParameters()) {
      PsiType exception = parameter.getType();
      for (int j = array.size() - 1; j >= 0; j--) {
        PsiClassType exception1 = array.get(j);
        if (exception.isAssignableFrom(exception1)) {
          array.remove(exception1);
        }
      }
    }

    for (PsiCodeBlock catchBlock : tryStatement.getCatchBlocks()) {
      addExceptions(array, getThrownExceptions(catchBlock));
    }

    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null) {
      // if finally block completes normally, exception not caught
      // if finally block completes abruptly, exception gets lost
      try {
        ControlFlow flow = ControlFlowFactory
          .getInstance(finallyBlock.getProject()).getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
        int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
        List<PsiClassType> thrownExceptions = getThrownExceptions(finallyBlock);
        if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) {
          array = ContainerUtil.newArrayList(thrownExceptions);
        }
        else {
          addExceptions(array, thrownExceptions);
        }
      }
      catch (AnalysisCanceledException e) {
        // incomplete code
      }
    }

    return array;
  }

  @NotNull
  private static List<PsiClassType> getExceptionsByMethodAndChildren(PsiElement element, JavaResolveResult resolveResult) {
    List<PsiClassType> result = ContainerUtil.newArrayList();

    PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method != null) {
      addExceptions(result, getExceptionsByMethod(method, resolveResult.getSubstitutor()));
    }

    addExceptions(result, getThrownExceptions(element.getChildren()));

    return result;
  }

  @NotNull
  private static List<PsiClassType> getExceptionsByMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    List<PsiClassType> result = ContainerUtil.newArrayList();

    PsiClassType[] referenceTypes = method.getThrowsList().getReferencedTypes();
    for (PsiType type : referenceTypes) {
      type = substitutor.substitute(type);
      if (type instanceof PsiClassType) {
        result.add((PsiClassType)type);
      }
    }

    return result;
  }

  private static void addExceptions(List<PsiClassType> array, Collection<PsiClassType> exceptions) {
    for (PsiClassType exception : exceptions) {
      addException(array, exception);
    }
  }

  private static void addException(List<PsiClassType> array, PsiClassType exception) {
    if (exception == null) return ;
    for (int i = array.size()-1; i>=0; i--) {
      PsiClassType exception1 = array.get(i);
      if (exception1.isAssignableFrom(exception)) return;
      if (exception.isAssignableFrom(exception1)) {
        array.remove(i);
      }
    }
    array.add(exception);
  }

  @NotNull
  public static Collection<PsiClassType> collectUnhandledExceptions(@NotNull PsiElement element, @Nullable PsiElement topElement) {
    return collectUnhandledExceptions(element, topElement, true);
  }

  @NotNull
  public static Collection<PsiClassType> collectUnhandledExceptions(@NotNull PsiElement element,
                                                                    @Nullable PsiElement topElement,
                                                                    boolean includeSelfCalls) {
    final Set<PsiClassType> set = collectUnhandledExceptions(element, topElement, null, includeSelfCalls);
    return set == null ? Collections.<PsiClassType>emptyList() : set;
  }

  @Nullable
  private static Set<PsiClassType> collectUnhandledExceptions(@NotNull PsiElement element,
                                                              @Nullable PsiElement topElement,
                                                              @Nullable Set<PsiClassType> foundExceptions,
                                                              boolean includeSelfCalls) {
    Collection<PsiClassType> unhandledExceptions = null;
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      unhandledExceptions = getUnhandledExceptions(expression, topElement, includeSelfCalls);
    }
    else if (element instanceof PsiMethodReferenceExpression) {
      unhandledExceptions = getUnhandledExceptions((PsiMethodReferenceExpression)element, topElement);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement statement = (PsiThrowStatement)element;
      unhandledExceptions = getUnhandledExceptions(statement, topElement);
    }
    else if (element instanceof PsiCodeBlock &&
             element.getParent() instanceof PsiMethod &&
             ((PsiMethod)element.getParent()).isConstructor() &&
             !firstStatementIsConstructorCall((PsiCodeBlock)element)) {
      // there is implicit parent constructor call
      final PsiMethod constructor = (PsiMethod)element.getParent();
      final PsiClass aClass = constructor.getContainingClass();
      final PsiClass superClass = aClass == null ? null : aClass.getSuperClass();
      final PsiMethod[] superConstructors = superClass == null ? PsiMethod.EMPTY_ARRAY : superClass.getConstructors();
      Set<PsiClassType> unhandled = new HashSet<PsiClassType>();
      for (PsiMethod superConstructor : superConstructors) {
        if (!superConstructor.hasModifierProperty(PsiModifier.PRIVATE) && superConstructor.getParameterList().getParametersCount() == 0) {
          final PsiClassType[] exceptionTypes = superConstructor.getThrowsList().getReferencedTypes();
          for (PsiClassType exceptionType : exceptionTypes) {
            if (!isUncheckedException(exceptionType) && !isHandled(element, exceptionType, topElement)) {
              unhandled.add(exceptionType);
            }
          }
          break;
        }
      }

      // plus all exceptions thrown in instance class initializers
      if (aClass != null) {
        final PsiClassInitializer[] initializers = aClass.getInitializers();
        final Set<PsiClassType> thrownByInitializer = new THashSet<PsiClassType>();
        for (PsiClassInitializer initializer : initializers) {
          if (initializer.hasModifierProperty(PsiModifier.STATIC)) continue;
          thrownByInitializer.clear();
          collectUnhandledExceptions(initializer.getBody(), initializer, thrownByInitializer, includeSelfCalls);
          for (PsiClassType thrown : thrownByInitializer) {
            if (!isHandled(constructor.getBody(), thrown, topElement)) {
              unhandled.add(thrown);
            }
          }
        }
      }
      unhandledExceptions = unhandled;
    }

    if (element instanceof PsiResourceVariable) {
      final List<PsiClassType> unhandled = getUnhandledCloserExceptions((PsiResourceVariable)element, topElement);
      if (!unhandled.isEmpty()) {
        if (unhandledExceptions == null) {
          unhandledExceptions = ContainerUtil.newArrayList(unhandled);
        }
        else {
          unhandledExceptions.addAll(unhandled);
        }
      }
    }

    if (unhandledExceptions != null) {
      if (foundExceptions == null) {
        foundExceptions = new THashSet<PsiClassType>();
      }
      foundExceptions.addAll(unhandledExceptions);
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      foundExceptions = collectUnhandledExceptions(child, topElement, foundExceptions, includeSelfCalls);
    }

    return foundExceptions;
  }

  private static Collection<PsiClassType> getUnhandledExceptions(PsiMethodReferenceExpression methodReferenceExpression,
                                                                 PsiElement topElement) {
    final JavaResolveResult resolveResult = methodReferenceExpression.advancedResolve(false);
    final PsiElement resolve = resolveResult.getElement();
    if (resolve instanceof PsiMethod) {
      return getUnhandledExceptions((PsiMethod)resolve, methodReferenceExpression, topElement, resolveResult.getSubstitutor());
    }
    return Collections.emptyList();
  }

  private static boolean firstStatementIsConstructorCall(PsiCodeBlock constructorBody) {
    final PsiStatement[] statements = constructorBody.getStatements();
    if (statements.length == 0) return false;
    if (!(statements[0] instanceof PsiExpressionStatement)) return false;

    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiMethod method = (PsiMethod)((PsiMethodCallExpression)expression).getMethodExpression().resolve();
    return method != null && method.isConstructor();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(PsiElement[] elements) {
    final List<PsiClassType> array = ContainerUtil.newArrayList();
    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitCallExpression(PsiCallExpression expression) {
        addExceptions(array, getUnhandledExceptions(expression, null));
        visitElement(expression);
      }

      @Override
      public void visitThrowStatement(PsiThrowStatement statement) {
        addExceptions(array, getUnhandledExceptions(statement, null));
        visitElement(statement);
      }

      @Override
      public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
        addExceptions(array, getUnhandledExceptions(expression, null));
        visitElement(expression);
      }

      @Override
      public void visitResourceVariable(PsiResourceVariable resourceVariable) {
        addExceptions(array, getUnhandledCloserExceptions(resourceVariable, null));
        visitElement(resourceVariable);
      }
    };

    for (PsiElement element : elements) {
      element.accept(visitor);
    }

    return array;
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(PsiElement element) {
    if (element instanceof PsiCallExpression) {
      PsiCallExpression expression = (PsiCallExpression)element;
      return getUnhandledExceptions(expression, null);
    }
    else if (element instanceof PsiThrowStatement) {
      PsiThrowStatement throwStatement = (PsiThrowStatement)element;
      return getUnhandledExceptions(throwStatement, null);
    }
    else if (element instanceof PsiResourceVariable) {
      return getUnhandledCloserExceptions((PsiResourceVariable)element, null);
    }

    return getUnhandledExceptions(new PsiElement[]{element});
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(final PsiCallExpression methodCall, @Nullable final PsiElement topElement) {
    return getUnhandledExceptions(methodCall, topElement, true);
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(final PsiCallExpression methodCall,
                                                          @Nullable final PsiElement topElement,
                                                          boolean includeSelfCalls) {
    final JavaResolveResult result = methodCall.resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)result.getElement();
    if (!includeSelfCalls && method == PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class)) {
      return Collections.emptyList();
    }

    final PsiSubstitutor substitutor = ApplicationManager.getApplication().runReadAction(new Computable<PsiSubstitutor>() {
      @Override
      public PsiSubstitutor compute() {
        return result.getSubstitutor();
      }
    });
    return getUnhandledExceptions(method, methodCall, topElement, substitutor);
  }

  @NotNull
  public static List<PsiClassType> getCloserExceptions(final PsiResourceVariable resource) {
    final PsiMethod method = PsiUtil.getResourceCloserMethod(resource);
    return method != null ? getExceptionsByMethod(method, PsiSubstitutor.EMPTY) : Collections.<PsiClassType>emptyList();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledCloserExceptions(final PsiResourceVariable resource, @Nullable final PsiElement topElement) {
    final PsiMethod method = PsiUtil.getResourceCloserMethod(resource);
    return method != null ? getUnhandledExceptions(method, resource, topElement, PsiSubstitutor.EMPTY) : Collections.<PsiClassType>emptyList();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(final PsiThrowStatement throwStatement, @Nullable final PsiElement topElement) {
    final PsiExpression exception = throwStatement.getException();
    final List<PsiType> types = getPreciseThrowTypes(exception);
    return ContainerUtil.mapNotNull(types, new NullableFunction<PsiType, PsiClassType>() {
      @Override
      public PsiClassType fun(PsiType type) {
        if (type instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)type;
          if (!isUncheckedException(classType) && !isHandled(throwStatement, classType, topElement)) {
            return classType;
          }
        }
        return null;
      }
    });
  }

  @NotNull
  private static List<PsiType> getPreciseThrowTypes(@Nullable final PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement target = ((PsiReferenceExpression)expression).resolve();
      if (target != null && PsiUtil.isCatchParameter(target)) {
        return ((PsiCatchSection)target.getParent()).getPreciseCatchTypes();
      }
    }

    if (expression != null) {
      final PsiType type = expression.getType();
      if (type != null) {
        return Arrays.asList(type);
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  private static List<PsiClassType> getUnhandledExceptions(PsiMethod method,
                                                           PsiElement element,
                                                           PsiElement topElement,
                                                           PsiSubstitutor substitutor) {
    if (method == null || isArrayClone(method, element)) {
      return Collections.emptyList();
    }
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    if (referencedTypes.length > 0) {
      List<PsiClassType> result = ContainerUtil.newArrayList();

      for (PsiClassType referencedType : referencedTypes) {
        final PsiType type = substitutor.substitute(referencedType);
        if (!(type instanceof PsiClassType)) continue;
        PsiClassType classType = (PsiClassType)type;
        PsiClass exceptionClass = ((PsiClassType)type).resolve();
        if (exceptionClass == null) continue;

        if (isUncheckedException(classType)) continue;
        if (isHandled(element, classType, topElement)) continue;

        result.add((PsiClassType)type);
      }

      return result;
    }
    return Collections.emptyList();
  }

  private static boolean isArrayClone(PsiMethod method, PsiElement element) {
    if (!method.getName().equals(CLONE_METHOD_NAME)) return false;
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || !CommonClassNames.JAVA_LANG_OBJECT.equals(containingClass.getQualifiedName())) {
      return false;
    }
    if (element instanceof PsiMethodReferenceExpression) {
      final PsiMethodReferenceExpression methodCallExpression = (PsiMethodReferenceExpression)element;
      final PsiExpression qualifierExpression = methodCallExpression.getQualifierExpression();
      return qualifierExpression != null && qualifierExpression.getType() instanceof PsiArrayType;
    }
    if (!(element instanceof PsiMethodCallExpression)) return false;

    PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
    final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
    return qualifierExpression != null && qualifierExpression.getType() instanceof PsiArrayType;
  }

  public static boolean isUncheckedException(@NotNull PsiClassType type) {
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) || InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_ERROR);
  }

  public static boolean isUncheckedExceptionOrSuperclass(@NotNull final PsiClassType type) {
    return isGeneralExceptionType(type) || isUncheckedException(type);
  }

  public static boolean isGeneralExceptionType(@NotNull final PsiType type) {
    final String canonicalText = type.getCanonicalText();
    return CommonClassNames.JAVA_LANG_THROWABLE.equals(canonicalText) ||
           CommonClassNames.JAVA_LANG_EXCEPTION.equals(canonicalText);
  }

  public static boolean isHandled(PsiClassType exceptionType, PsiElement throwPlace) {
    return isHandled(throwPlace, exceptionType, throwPlace.getContainingFile());
  }

  private static boolean isHandled(PsiElement element, PsiClassType exceptionType, PsiElement topElement) {
    if (element == null || element.getParent() == topElement || element.getParent() == null) return false;

    final PsiElement parent = element.getParent();

    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      return isHandledByMethodThrowsClause(method, exceptionType);
    }
    else if (parent instanceof PsiClass) {
      // arguments to anon class constructor should be handled higher
      // like in void f() throws XXX { new AA(methodThrowingXXX()) { ... }; }
      return parent instanceof PsiAnonymousClass && isHandled(parent, exceptionType, topElement);
    }
    else if (parent instanceof PsiLambdaExpression) {
      final PsiType interfaceType = ((PsiLambdaExpression)parent).getFunctionalInterfaceType();
      return isDeclaredBySAMMethod(exceptionType, interfaceType);
    }
    else if (element instanceof PsiMethodReferenceExpression) {
      final PsiType interfaceType = ((PsiMethodReferenceExpression)element).getFunctionalInterfaceType();
      return isDeclaredBySAMMethod(exceptionType, interfaceType);
    }
    else if (parent instanceof PsiClassInitializer) {
      if (((PsiClassInitializer)parent).hasModifierProperty(PsiModifier.STATIC)) return false;
      // anonymous class initializers can throw any exceptions
      if (!(parent.getParent() instanceof PsiAnonymousClass)) {
        // exception thrown from within class instance initializer must be handled in every class constructor
        // check each constructor throws exception or superclass (there must be at least one)
        final PsiClass aClass = ((PsiClassInitializer)parent).getContainingClass();
        return areAllConstructorsThrow(aClass, exceptionType);
      }
    }
    else if (parent instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)parent;
      if (tryStatement.getTryBlock() == element && isCaught(tryStatement, exceptionType)) {
        return true;
      }
      if (tryStatement.getResourceList() == element && isCaught(tryStatement, exceptionType)) {
        return true;
      }
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (element instanceof PsiCatchSection && finallyBlock != null && blockCompletesAbruptly(finallyBlock)) {
        // exception swallowed
        return true;
      }
    }
    else if (parent instanceof JavaCodeFragment) {
      JavaCodeFragment codeFragment = (JavaCodeFragment)parent;
      JavaCodeFragment.ExceptionHandler exceptionHandler = codeFragment.getExceptionHandler();
      return exceptionHandler != null && exceptionHandler.isHandledException(exceptionType);
    }
    else if (PsiImplUtil.isInServerPage(parent) && parent instanceof PsiFile) {
      return true;
    }
    else if (parent instanceof PsiFile) {
      return false;
    }
    else if (parent instanceof PsiField && ((PsiField)parent).getInitializer() == element) {
      final PsiClass aClass = ((PsiField)parent).getContainingClass();
      if (aClass != null && !(aClass instanceof PsiAnonymousClass) && !((PsiField)parent).hasModifierProperty(PsiModifier.STATIC)) {
        // exceptions thrown in field initializers should be thrown in all class constructors
        return areAllConstructorsThrow(aClass, exceptionType);
      }
    }
    return isHandled(parent, exceptionType, topElement);
  }

  private static boolean isDeclaredBySAMMethod(PsiClassType exceptionType, PsiType interfaceType) {
    if (interfaceType != null) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
      if (interfaceMethod != null) {
        return isHandledByMethodThrowsClause(interfaceMethod, exceptionType);
      }
    }
    return true;
  }

  private static boolean areAllConstructorsThrow(final PsiClass aClass, PsiClassType exceptionType) {
    if (aClass == null) return false;
    final PsiMethod[] constructors = aClass.getConstructors();
    boolean thrown = constructors.length != 0;
    for (PsiMethod constructor : constructors) {
      if (!isHandledByMethodThrowsClause(constructor, exceptionType)) {
        thrown = false;
        break;
      }
    }
    return thrown;
  }

  private static boolean isCaught(PsiTryStatement tryStatement, PsiClassType exceptionType) {
    // if finally block completes abruptly, exception gets lost
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null && blockCompletesAbruptly(finallyBlock)) return true;

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();
    for (PsiParameter parameter : catchBlockParameters) {
      PsiType paramType = parameter.getType();
      if (paramType.isAssignableFrom(exceptionType)) return true;
    }

    return false;
  }

  private static boolean blockCompletesAbruptly(final PsiCodeBlock finallyBlock) {
    try {
      ControlFlow flow = ControlFlowFactory.getInstance(finallyBlock.getProject()).getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
      int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
      if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) return true;
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    return false;
  }

  private static boolean isHandledByMethodThrowsClause(PsiMethod method, PsiClassType exceptionType) {
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return isHandledBy(exceptionType, referencedTypes);
  }

  public static boolean isHandledBy(PsiClassType exceptionType, @NotNull PsiClassType[] referencedTypes) {
    for (PsiClassType classType : referencedTypes) {
      if (classType.isAssignableFrom(exceptionType)) return true;
    }
    return false;
  }

  public static void sortExceptionsByHierarchy(List<PsiClassType> exceptions) {
    if (exceptions.size() <= 1) return;
    sortExceptionsByHierarchy(exceptions.subList(1, exceptions.size()));
    for (int i=0; i<exceptions.size()-1;i++) {
      if (TypeConversionUtil.isAssignable(exceptions.get(i), exceptions.get(i+1))) {
        Collections.swap(exceptions, i,i+1);
      }
    }
  }
}
