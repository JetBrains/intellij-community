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
package com.intellij.codeInsight;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.MethodProcessorSetupFailedException;
import com.intellij.psi.scope.processor.MethodResolverProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.util.Pair.pair;

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
  private static List<PsiClassType> filterOutUncheckedExceptions(@NotNull List<PsiClassType> exceptions) {
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
    else if (element instanceof PsiLambdaExpression) {
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
      List<PsiClassType> classTypes =
        new ArrayList<PsiClassType>(ContainerUtil.mapNotNull(types, new NullableFunction<PsiType, PsiClassType>() {
          @Override
          public PsiClassType fun(PsiType type) {
            return type instanceof PsiClassType ? (PsiClassType)type : null;
          }
        }));
      addExceptions(classTypes, getThrownExceptions(expr));
      return classTypes;
    }
    else if (element instanceof PsiTryStatement) {
      return getTryExceptions((PsiTryStatement)element);
    }
    else if (element instanceof PsiResourceListElement) {
      List<PsiClassType> types = ContainerUtil.newArrayList();
      addExceptions(types, getCloserExceptions((PsiResourceListElement)element));
      if (element instanceof PsiResourceVariable) {
        PsiResourceVariable variable = (PsiResourceVariable)element;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          addExceptions(types, getThrownExceptions(initializer));
        }
      }
      return types;
    }

    return getThrownExceptions(element.getChildren());
  }

  @NotNull
  private static List<PsiClassType> getTryExceptions(@NotNull PsiTryStatement tryStatement) {
    List<PsiClassType> array = ContainerUtil.newArrayList();

    PsiResourceList resourceList = tryStatement.getResourceList();
    if (resourceList != null) {
      for (PsiResourceListElement resource : resourceList) {
        addExceptions(array, getUnhandledCloserExceptions(resource, resourceList));
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
  private static List<PsiClassType> getExceptionsByMethodAndChildren(@NotNull PsiElement element, @NotNull JavaResolveResult resolveResult) {
    List<PsiClassType> result = ContainerUtil.newArrayList();

    PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method != null) {
      addExceptions(result, getExceptionsByMethod(method, resolveResult.getSubstitutor(), element));
    }

    addExceptions(result, getThrownExceptions(element.getChildren()));

    return result;
  }

  @NotNull
  private static List<PsiClassType> getExceptionsByMethod(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor,
                                                          @NotNull PsiElement place) {
    PsiClassType[] referenceTypes = method.getThrowsList().getReferencedTypes();
    if (referenceTypes.length == 0) return Collections.emptyList();

    GlobalSearchScope scope = place.getResolveScope();

    List<PsiClassType> result = ContainerUtil.newArrayList();
    for (PsiType type : referenceTypes) {
      type = PsiClassImplUtil.correctType(substitutor.substitute(type), scope);
      if (type instanceof PsiClassType) {
        result.add((PsiClassType)type);
      }
    }

    return result;
  }

  private static void addExceptions(@NotNull List<PsiClassType> array, @NotNull Collection<PsiClassType> exceptions) {
    for (PsiClassType exception : exceptions) {
      addException(array, exception);
    }
  }

  private static void addException(@NotNull List<PsiClassType> array, @Nullable PsiClassType exception) {
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
      PsiExpression qualifierExpression = ((PsiMethodReferenceExpression)element).getQualifierExpression();
      return qualifierExpression != null ? collectUnhandledExceptions(qualifierExpression, topElement, null, false) 
                                         : null;
    }
    else if (element instanceof PsiLambdaExpression) {
      return null;
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
    else if (element instanceof PsiResourceListElement) {
      final List<PsiClassType> unhandled = getUnhandledCloserExceptions((PsiResourceListElement)element, topElement);
      if (!unhandled.isEmpty()) {
        unhandledExceptions = ContainerUtil.newArrayList(unhandled);
      }
    }

    if (unhandledExceptions != null) {
      if (foundExceptions == null) {
        foundExceptions = new THashSet<PsiClassType>();
      }
      foundExceptions.addAll(unhandledExceptions);
    }

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      Set<PsiClassType> foundInChild = collectUnhandledExceptions(child, topElement, foundExceptions, includeSelfCalls);
      if (foundExceptions == null) {
        foundExceptions = foundInChild;
      }
      else if (foundInChild != null) {
        foundExceptions.addAll(foundInChild);
      }
    }

    return foundExceptions;
  }

  @NotNull
  private static Collection<PsiClassType> getUnhandledExceptions(@NotNull PsiMethodReferenceExpression methodReferenceExpression,
                                                                 PsiElement topElement) {
    final JavaResolveResult resolveResult = methodReferenceExpression.advancedResolve(false);
    final PsiElement resolve = resolveResult.getElement();
    if (resolve instanceof PsiMethod) {
      final PsiElement referenceNameElement = methodReferenceExpression.getReferenceNameElement();
      return getUnhandledExceptions((PsiMethod)resolve, referenceNameElement, topElement, resolveResult.getSubstitutor());
    }
    return Collections.emptyList();
  }

  private static boolean firstStatementIsConstructorCall(@NotNull PsiCodeBlock constructorBody) {
    final PsiStatement[] statements = constructorBody.getStatements();
    if (statements.length == 0) return false;
    if (!(statements[0] instanceof PsiExpressionStatement)) return false;

    final PsiExpression expression = ((PsiExpressionStatement)statements[0]).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return false;
    final PsiMethod method = (PsiMethod)((PsiMethodCallExpression)expression).getMethodExpression().resolve();
    return method != null && method.isConstructor();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(final @NotNull PsiElement[] elements) {
    final List<PsiClassType> array = ContainerUtil.newArrayList();

    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitCallExpression(@NotNull PsiCallExpression expression) {
        addExceptions(array, getUnhandledExceptions(expression, null));
        visitElement(expression);
      }

      @Override
      public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
        addExceptions(array, getUnhandledExceptions(statement, null));
        visitElement(statement);
      }

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {
        if (ArrayUtil.find(elements, expression) >= 0) {
          visitElement(expression);
        }
      }

      @Override
      public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
        if (ArrayUtil.find(elements, expression) >= 0) {
          addExceptions(array, getUnhandledExceptions(expression, null));
          visitElement(expression);
        }
      }

      @Override
      public void visitResourceVariable(@NotNull PsiResourceVariable resource) {
        addExceptions(array, getUnhandledCloserExceptions(resource, null));
        visitElement(resource);
      }

      @Override
      public void visitResourceExpression(@NotNull PsiResourceExpression resource) {
        addExceptions(array, getUnhandledCloserExceptions(resource, null));
        visitElement(resource);
      }

      @Override
      public void visitClass(PsiClass aClass) { }
    };

    for (PsiElement element : elements) {
      element.accept(visitor);
    }

    return array;
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(@NotNull PsiElement element) {
    return getUnhandledExceptions(new PsiElement[]{element});
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(@NotNull final PsiCallExpression methodCall, @Nullable final PsiElement topElement) {
    return getUnhandledExceptions(methodCall, topElement, true);
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(@NotNull final PsiCallExpression methodCall,
                                                          @Nullable final PsiElement topElement,
                                                          final boolean includeSelfCalls) {
    //exceptions only influence the invocation type after overload resolution is complete
    if (MethodCandidateInfo.isOverloadCheck()) {
      return Collections.emptyList();
    }
    final MethodCandidateInfo.CurrentCandidateProperties properties = MethodCandidateInfo.getCurrentMethod(methodCall.getArgumentList());
    final JavaResolveResult result = properties != null ? properties.getInfo() : InferenceSession.getResolveResult(methodCall);
    final PsiElement element = result.getElement();
    final PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : null;
    if (method == null) {
      return Collections.emptyList();
    }
    final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    if (!includeSelfCalls && method == containingMethod) {
      return Collections.emptyList();
    }

    final PsiClassType[] thrownExceptions = method.getThrowsList().getReferencedTypes();
    if (thrownExceptions.length == 0) {
      return Collections.emptyList();
    }

    final PsiSubstitutor substitutor = result.getSubstitutor();
    if (!isArrayClone(method, methodCall) && methodCall instanceof PsiMethodCallExpression) {
      final PsiFile containingFile = (containingMethod == null ? methodCall : containingMethod).getContainingFile();
      final MethodResolverProcessor processor = new MethodResolverProcessor((PsiMethodCallExpression)methodCall, containingFile);
      try {
        PsiScopesUtil.setupAndRunProcessor(processor, methodCall, false);
        final List<Pair<PsiMethod, PsiSubstitutor>> candidates = ContainerUtil.mapNotNull(
          processor.getResults(), new Function<CandidateInfo, Pair<PsiMethod, PsiSubstitutor>>() {
          @Override
          public Pair<PsiMethod, PsiSubstitutor> fun(CandidateInfo info) {
            PsiElement element = info.getElement();
            if (element instanceof PsiMethod &&
                MethodSignatureUtil.areSignaturesEqual(method, (PsiMethod)element) &&
                !MethodSignatureUtil.isSuperMethod((PsiMethod)element, method)) {
              return Pair.create((PsiMethod)element, info.getSubstitutor());
            }
            return null;
          }
        });
        if (candidates.size() > 1) {
          GlobalSearchScope scope = methodCall.getResolveScope();
          final List<PsiClassType> ex = collectSubstituted(substitutor, thrownExceptions, scope);
          for (Pair<PsiMethod, PsiSubstitutor> pair : candidates) {
            final PsiClassType[] exceptions = pair.first.getThrowsList().getReferencedTypes();
            if (exceptions.length == 0) {
              return getUnhandledExceptions(methodCall, topElement, PsiSubstitutor.EMPTY, PsiClassType.EMPTY_ARRAY);
            }
            retainExceptions(ex, collectSubstituted(pair.second, exceptions, scope));
          }
          return getUnhandledExceptions(methodCall, topElement, PsiSubstitutor.EMPTY, ex.toArray(new PsiClassType[ex.size()]));
        }
      }
      catch (MethodProcessorSetupFailedException ignore) {
        return Collections.emptyList();
      }
    }

    return getUnhandledExceptions(method, methodCall, topElement, substitutor);
  }

  public static void retainExceptions(List<PsiClassType> ex, List<PsiClassType> thrownEx) {
    final List<PsiClassType> replacement = new ArrayList<PsiClassType>();
    for (Iterator<PsiClassType> iterator = ex.iterator(); iterator.hasNext(); ) {
      PsiClassType classType = iterator.next();
      boolean found = false;
      for (PsiClassType psiClassType : thrownEx) {
        if (psiClassType.isAssignableFrom(classType)) {
          found = true;
          break;
        } else if (classType.isAssignableFrom(psiClassType)) {
          if (isUncheckedException(classType) == isUncheckedException(psiClassType)) {
            replacement.add(psiClassType);
            iterator.remove();
          }
          found = true;
          break;
        }
      }
      if (!found) {
        iterator.remove();
      }
    }
    ex.addAll(replacement);
  }

  public static List<PsiClassType> collectSubstituted(PsiSubstitutor substitutor, PsiClassType[] thrownExceptions, GlobalSearchScope scope) {
    final List<PsiClassType> ex = new ArrayList<PsiClassType>();
    for (PsiClassType thrownException : thrownExceptions) {
      final PsiType psiType = PsiClassImplUtil.correctType(substitutor.substitute(thrownException), scope);
      if (psiType instanceof PsiClassType) {
        ex.add((PsiClassType)psiType);
      }
    }
    return ex;
  }

  @NotNull
  public static List<PsiClassType> getCloserExceptions(@NotNull PsiResourceListElement resource) {
    Pair<PsiMethod, PsiSubstitutor> closer = resolveCloser(resource);
    return closer != null ? getExceptionsByMethod(closer.first, closer.second, resource) : Collections.<PsiClassType>emptyList();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledCloserExceptions(@NotNull PsiResourceListElement resource, @Nullable PsiElement topElement) {
    Pair<PsiMethod, PsiSubstitutor> closer = resolveCloser(resource);
    return closer != null ? getUnhandledExceptions(closer.first, resource, topElement, closer.second) : Collections.<PsiClassType>emptyList();
  }

  private static Pair<PsiMethod, PsiSubstitutor> resolveCloser(PsiResourceListElement resource) {
    PsiMethod method = PsiUtil.getResourceCloserMethod(resource);
    if (method != null) {
      PsiClass closerClass = method.getContainingClass();
      if (closerClass != null) {
        PsiClassType.ClassResolveResult resourceType = PsiUtil.resolveGenericsClassInType(resource.getType());
        if (resourceType != null) {
          PsiClass resourceClass = resourceType.getElement();
          if (resourceClass != null) {
            PsiSubstitutor substitutor = TypeConversionUtil.getClassSubstitutor(closerClass, resourceClass, resourceType.getSubstitutor());
            if (substitutor != null) {
              return pair(method, substitutor);
            }
          }
        }
      }
    }

    return null;
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(@NotNull PsiThrowStatement throwStatement, @Nullable PsiElement topElement) {
    List<PsiClassType> unhandled = new SmartList<PsiClassType>();
    for (PsiType type : getPreciseThrowTypes(throwStatement.getException())) {
      List<PsiType> types = type instanceof PsiDisjunctionType ? ((PsiDisjunctionType)type).getDisjunctions() : Collections.singletonList(type);
      for (PsiType subType : types) {
        if (subType instanceof PsiClassType) {
          PsiClassType classType = (PsiClassType)subType;
          if (!isUncheckedException(classType) && !isHandled(throwStatement, classType, topElement)) {
            unhandled.add(classType);
          }
        }
      }
    }
    return unhandled;
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
        return Collections.singletonList(type);
      }
    }

    return Collections.emptyList();
  }

  @NotNull
  public static List<PsiClassType> getUnhandledExceptions(@NotNull PsiMethod method,
                                                          PsiElement element,
                                                          PsiElement topElement,
                                                          @NotNull PsiSubstitutor substitutor) {
    if (isArrayClone(method, element)) {
      return Collections.emptyList();
    }
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return getUnhandledExceptions(element, topElement, substitutor, referencedTypes);
  }

  private static List<PsiClassType> getUnhandledExceptions(PsiElement element,
                                                           PsiElement topElement,
                                                           PsiSubstitutor substitutor,
                                                           PsiClassType[] referencedTypes) {
    if (referencedTypes.length > 0) {
      List<PsiClassType> result = ContainerUtil.newArrayList();

      for (PsiClassType referencedType : referencedTypes) {
        final PsiType type = PsiClassImplUtil.correctType(GenericsUtil.eliminateWildcards(substitutor.substitute(referencedType), false), element.getResolveScope());
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

  private static boolean isArrayClone(@NotNull PsiMethod method, PsiElement element) {
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

  public static boolean isHandled(@NotNull PsiClassType exceptionType, @NotNull PsiElement throwPlace) {
    return isHandled(throwPlace, exceptionType, throwPlace.getContainingFile());
  }

  private static boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
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
    else if (parent instanceof PsiLambdaExpression ||
             parent instanceof PsiMethodReferenceExpression && element == ((PsiMethodReferenceExpression)parent).getReferenceNameElement()) {
      final PsiType interfaceType = ((PsiFunctionalExpression)parent).getFunctionalInterfaceType();
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
    } else {
      for (CustomExceptionHandler exceptionHandler : Extensions.getExtensions(CustomExceptionHandler.KEY)) {
        if (exceptionHandler.isHandled(element, exceptionType, topElement)) return true;
      }
    }
    return isHandled(parent, exceptionType, topElement);
  }

  private static boolean isDeclaredBySAMMethod(@NotNull PsiClassType exceptionType, @Nullable PsiType interfaceType) {
    if (interfaceType != null) {
      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(interfaceType);
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      if (interfaceMethod != null) {
        return isHandledByMethodThrowsClause(interfaceMethod, exceptionType, LambdaUtil.getSubstitutor(interfaceMethod, resolveResult));
      }
    }
    return true;
  }

  private static boolean areAllConstructorsThrow(@Nullable final PsiClass aClass, @NotNull PsiClassType exceptionType) {
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

  private static boolean isCaught(@NotNull PsiTryStatement tryStatement, @NotNull PsiClassType exceptionType) {
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

  private static boolean blockCompletesAbruptly(@NotNull final PsiCodeBlock finallyBlock) {
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

  private static boolean isHandledByMethodThrowsClause(@NotNull PsiMethod method, @NotNull PsiClassType exceptionType) {
    return isHandledByMethodThrowsClause(method, exceptionType, PsiSubstitutor.EMPTY);
  }

  private static boolean isHandledByMethodThrowsClause(@NotNull PsiMethod method,
                                                       @NotNull PsiClassType exceptionType,
                                                       PsiSubstitutor substitutor) {
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return isHandledBy(exceptionType, referencedTypes, substitutor);
  }

  public static boolean isHandledBy(@NotNull PsiClassType exceptionType, @NotNull PsiClassType[] referencedTypes) {
    return isHandledBy(exceptionType, referencedTypes, PsiSubstitutor.EMPTY);
  }

  public static boolean isHandledBy(@NotNull PsiClassType exceptionType,
                                    @NotNull PsiClassType[] referencedTypes,
                                    PsiSubstitutor substitutor) {
    for (PsiClassType classType : referencedTypes) {
      PsiType psiType = substitutor.substitute(classType);
      if (psiType != null && psiType.isAssignableFrom(exceptionType)) return true;
    }
    return false;
  }

  public static void sortExceptionsByHierarchy(@NotNull List<PsiClassType> exceptions) {
    if (exceptions.size() <= 1) return;
    sortExceptionsByHierarchy(exceptions.subList(1, exceptions.size()));
    for (int i=0; i<exceptions.size()-1;i++) {
      if (TypeConversionUtil.isAssignable(exceptions.get(i), exceptions.get(i+1))) {
        Collections.swap(exceptions, i,i+1);
      }
    }
  }
}
