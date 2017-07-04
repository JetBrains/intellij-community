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
package com.intellij.slicer;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSubstitutorImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author cdr
 */
class SliceUtil {
  static boolean processUsagesFlownDownTo(@NotNull PsiElement expression,
                                          @NotNull Processor<SliceUsage> processor,
                                          @NotNull JavaSliceUsage parent,
                                          @NotNull PsiSubstitutor parentSubstitutor,
                                          int indexNesting,
                                          @NotNull String syntheticField) {
    assert indexNesting >= 0 : indexNesting;
    expression = simplify(expression);
    PsiElement original = expression;
    if (expression instanceof PsiArrayAccessExpression) {
      // now start tracking the array instead of element
      expression = ((PsiArrayAccessExpression)expression).getArrayExpression();
      indexNesting++;
    }
    PsiElement par = expression == null ? null : expression.getParent();
    if (expression instanceof PsiExpressionList && par instanceof PsiMethodCallExpression) {
      // expression list ends up here if we track varargs
      PsiMethod method = ((PsiMethodCallExpression)par).resolveMethod();
      if (method != null) {
        int parametersCount = method.getParameterList().getParametersCount();
        if (parametersCount != 0) {
          // unfold varargs list into individual expressions
          PsiExpression[] expressions = ((PsiExpressionList)expression).getExpressions();
          if (indexNesting != 0) {
            // should skip not-vararg arguments
            for (int i = parametersCount-1; i < expressions.length; i++) {
              PsiExpression arg = expressions[i];
              if (!handToProcessor(arg, processor, parent, parentSubstitutor, indexNesting - 1, syntheticField)) return false;
            }
          }
          return true;
        }
      }
    }

    boolean needToReportDeclaration = false;
    if (expression instanceof PsiReferenceExpression) {
      PsiElement element = SliceForwardUtil.complexify(expression);
      if (element instanceof PsiExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)element)) {
        PsiExpression rightSide = ((PsiAssignmentExpression)element.getParent()).getRExpression();
        return rightSide == null || handToProcessor(rightSide, processor, parent, parentSubstitutor, indexNesting, syntheticField);
      }
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      JavaResolveResult result = ref.advancedResolve(false);
      parentSubstitutor = result.getSubstitutor().putAll(parentSubstitutor);
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiCompiledElement) {
        resolved = resolved.getNavigationElement();
      }
      if (resolved instanceof PsiMethod && expression.getParent() instanceof PsiMethodCallExpression) {
        return processUsagesFlownDownTo(expression.getParent(), processor, parent, parentSubstitutor, indexNesting, syntheticField);
      }
      if (!(resolved instanceof PsiVariable)) return true;
      // check for container item modifications, like "array[i] = expression;"
      addContainerReferences((PsiVariable)resolved, processor, parent, parentSubstitutor, indexNesting, syntheticField);

      needToReportDeclaration = true;
      expression = resolved;
    }
    if (expression instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)expression;
      Collection<PsiExpression> values = DfaUtil.getCachedVariableValues(variable, original);
      if (values == null) {
        SliceUsage stopUsage = createTooComplexDFAUsage(expression, parent);
        return processor.process(stopUsage);
      }
      PsiExpression initializer = variable.getInitializer();
      if (values.isEmpty() && initializer != null) {
        values = Collections.singletonList(initializer);
      }
      boolean initializerReported = false;
      // no need to search and report assignments if we are going to report the variable anyway - it would create duplicate nodes if we did
      if (values.isEmpty() && !(variable instanceof PsiParameter) && !needToReportDeclaration) {
        values = DfaPsiUtil.getVariableAssignmentsInFile(variable, false, variable.getContainingFile().getLastChild());
        initializerReported = !values.isEmpty();
      }
      else if (!values.isEmpty() && !(variable instanceof PsiParameter)) {
        needToReportDeclaration = false; // already found all values
      }
      for (PsiExpression exp : values) {
        if (!handToProcessor(exp, processor, parent, parentSubstitutor, indexNesting, syntheticField)) return false;
        if (exp == initializer) initializerReported = true;
      }

      if (!initializerReported && needToReportDeclaration) { // otherwise we'll reach var declaration anyway because it is the initializer' parent
        // parameter or variable declaration can be far away from its usage (except for variable initializer) so report it separately
        return handToProcessor(variable, processor, parent, parentSubstitutor, indexNesting, syntheticField);
      }

      if (variable instanceof PsiField) {
        return processFieldUsages((PsiField)variable, parent, parentSubstitutor, processor);
      }
      else if (variable instanceof PsiParameter) {
        return processParameterUsages((PsiParameter)variable, parent, parentSubstitutor, indexNesting, syntheticField, processor);
      }
    }
    if (expression instanceof PsiMethodCallExpression) { // ctr call can't return value or be container get, so don't use PsiCall here
      PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
      Flow anno = method == null ? null : isMethodFlowAnnotated(method);
      if (anno != null) {
        String target = anno.target();
        if (target.equals(Flow.DEFAULT_TARGET)) target = Flow.RETURN_METHOD_TARGET;
        if (target.equals(Flow.RETURN_METHOD_TARGET)) {
          PsiExpression qualifier = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
          if (qualifier != null) {
            int nesting = calcNewIndexNesting(indexNesting, anno);
            String source = anno.source();
            if (source.equals(Flow.DEFAULT_SOURCE)) source = Flow.THIS_SOURCE;
            String synthetic = StringUtil.trimStart(StringUtil.trimStart(source, Flow.THIS_SOURCE),".");
            return processUsagesFlownDownTo(qualifier, processor, parent, parentSubstitutor, nesting, synthetic);
          }
        }
      }
      return processMethodReturnValue((PsiMethodCallExpression)expression, processor, parent, parentSubstitutor);
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      PsiExpression thenE = conditional.getThenExpression();
      PsiExpression elseE = conditional.getElseExpression();
      if (thenE != null && !handToProcessor(thenE, processor, parent, parentSubstitutor, indexNesting, syntheticField)) return false;
      if (elseE != null && !handToProcessor(elseE, processor, parent, parentSubstitutor, indexNesting, syntheticField)) return false;
    }
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      IElementType tokenType = assignment.getOperationTokenType();
      PsiExpression rExpression = assignment.getRExpression();

      if (tokenType == JavaTokenType.EQ && rExpression != null) {
        return processUsagesFlownDownTo(rExpression, processor, parent, parentSubstitutor, indexNesting, syntheticField);
      }
    }
    if (indexNesting != 0) {
      // consider container creation
      PsiElement initializer = expression instanceof PsiNewExpression ? ((PsiNewExpression)expression).getArrayInitializer() : expression;
      if (initializer instanceof PsiArrayInitializerExpression) {
        for (PsiExpression init : ((PsiArrayInitializerExpression)initializer).getInitializers()) {
          if (!handToProcessor(init, processor, parent, parentSubstitutor, indexNesting - 1, syntheticField)) return false;
        }
      }

      // check for constructor put arguments
      if (expression instanceof PsiNewExpression &&
          !processContainerPutArguments((PsiNewExpression)expression, parent, parentSubstitutor, indexNesting, syntheticField, processor)) {
        return false;
      }
    }
    return true;
  }

  private static Flow isMethodFlowAnnotated(@NotNull PsiMethod method) {
    return AnnotationUtil.findAnnotationInHierarchy(method, Flow.class);
  }

  private static Flow isParamFlowAnnotated(@NotNull PsiMethod method, int paramIndex) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length <= paramIndex) {
      if (parameters.length != 0 && parameters[parameters.length-1].isVarArgs()) {
        paramIndex = parameters.length-1;
      }
      else {
        return null;
      }
    }
    return AnnotationUtil.findAnnotationInHierarchy(parameters[paramIndex], Flow.class);
  }

  private static PsiElement simplify(@NotNull PsiElement expression) {
    if (expression instanceof PsiParenthesizedExpression) {
      return simplify(((PsiParenthesizedExpression)expression).getExpression());
    }
    if (expression instanceof PsiTypeCastExpression) {
      return simplify(((PsiTypeCastExpression)expression).getOperand());
    }
    return expression;
  }

  private static boolean handToProcessor(@NotNull PsiElement expression,
                                         @NotNull Processor<SliceUsage> processor,
                                         @NotNull SliceUsage parent,
                                         @NotNull PsiSubstitutor substitutor,
                                         int indexNesting,
                                         @NotNull String syntheticField) {
    final PsiElement realExpression = expression.getParent() instanceof DummyHolder ? expression.getParent().getContext() : expression;
    assert realExpression != null;
    if (!(realExpression instanceof PsiCompiledElement)) {
      SliceUsage usage = createSliceUsage(realExpression, parent, substitutor, indexNesting, syntheticField);
      if (!processor.process(usage)) return false;
    }
    return true;
  }

  public static Collection<SliceUsage> collectMethodReturnValues(@NotNull SliceUsage parent,
                                                                 @NotNull PsiSubstitutor parentSubstitutor,
                                                                 PsiMethod methodCalled) {
    CommonProcessors.CollectProcessor<SliceUsage> processor = new CommonProcessors.CollectProcessor<>();
    processMethodReturnValue(processor, parent, parentSubstitutor, null, methodCalled, null, PsiSubstitutor.EMPTY);
    return processor.getResults();
  }

  private static boolean processMethodReturnValue(@NotNull final PsiMethodCallExpression methodCallExpr,
                                                  @NotNull final Processor<SliceUsage> processor,
                                                  @NotNull final JavaSliceUsage parent,
                                                  @NotNull final PsiSubstitutor parentSubstitutor) {
    // if the call looks like 'otherClassObject.methodFromInterface()'
    // we can narrow down the overridden methods scan to inheritors of OtherClass only
    PsiClass qualifierClass = resolveQualifier(methodCallExpr);
    JavaResolveResult resolved = methodCallExpr.resolveMethodGenerics();
    PsiElement r = resolved.getElement();
    if (r instanceof PsiCompiledElement) {
      r = r.getNavigationElement();
    }
    if (!(r instanceof PsiMethod)) return true;
    PsiMethod methodCalled = (PsiMethod)r;

    PsiType returnType = methodCalled.getReturnType();
    if (returnType == null) return true;

    final PsiType parentType = parentSubstitutor.substitute(methodCallExpr.getType());
    final PsiSubstitutor substitutor = resolved.getSubstitutor().putAll(parentSubstitutor);
    return processMethodReturnValue(processor, parent, parentSubstitutor, qualifierClass, methodCalled, parentType, substitutor);
  }

  private static boolean processMethodReturnValue(@NotNull Processor<SliceUsage> processor,
                                                  @NotNull SliceUsage parent,
                                                  @NotNull PsiSubstitutor parentSubstitutor,
                                                  @Nullable PsiClass qualifierClass,
                                                  PsiMethod methodCalled,
                                                  @Nullable PsiType parentType,
                                                  @NotNull PsiSubstitutor substitutor) {
    Collection<PsiMethod> overrides = new THashSet<>();
    OverridingMethodsSearch.search(methodCalled, parent.getScope().toSearchScope(), true).forEach((PsiMethod override) -> {
      PsiClass containingClass = override.getContainingClass();
      if (containingClass == null) return true;
      if (qualifierClass == null || containingClass.isInheritor(qualifierClass, true)) {
        overrides.add(override);
      }
      return true;
    });
    overrides.add(methodCalled);

    int indexNesting = parent instanceof JavaSliceUsage ? ((JavaSliceUsage)parent).indexNesting : 0;

    final boolean[] result = {true};
    for (PsiMethod override : overrides) {
      if (!result[0]) break;
      if (override instanceof PsiCompiledElement) {
        override = (PsiMethod)override.getNavigationElement();
      }
      if (!parent.getScope().contains(override)) continue;

      Language language = override.getLanguage();
      if (language != JavaLanguage.INSTANCE) {
        handToProcessor(override, processor, parent, substitutor, indexNesting, "");
        continue;
      }

      final PsiCodeBlock body = override.getBody();
      if (body == null) continue;

      final PsiSubstitutor s = methodCalled == override ? substitutor :
                               MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodCalled.getSignature(substitutor), override.getSignature(substitutor));
      final PsiSubstitutor superSubstitutor = s == null ? parentSubstitutor : s;

      body.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitClass(PsiClass aClass) {}

        @Override
        public void visitLambdaExpression(PsiLambdaExpression expression) {}

        @Override
        public void visitReturnStatement(final PsiReturnStatement statement) {
          PsiExpression returnValue = statement.getReturnValue();
          if (returnValue == null) return;
          PsiType right = superSubstitutor.substitute(superSubstitutor.substitute(returnValue.getType()));
          if (right == null || (parentType != null && !TypeConversionUtil.isAssignable(parentType, right))) return;
          if (!handToProcessor(returnValue, processor, parent, substitutor, indexNesting, "")) {
            stopWalking();
            result[0] = false;
          }
        }
      });
    }

    return result[0];
  }

  private static PsiClass resolveQualifier(@NotNull PsiMethodCallExpression expr) {
    PsiExpression qualifier = expr.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      PsiMethodCallExpression copy = (PsiMethodCallExpression)expr.copy();
      PsiReferenceExpression methodExpression = copy.getMethodExpression();

      PsiThisExpression thisExpression = RefactoringChangeUtil.createThisExpression(expr.getManager(), null);
      methodExpression.setQualifierExpression(thisExpression);
      qualifier = methodExpression.getQualifierExpression();
    }
    if (qualifier != null) {
      if (qualifier instanceof PsiReferenceExpression) {
        PsiElement resolved = ((PsiReferenceExpression)qualifier).resolve();
        if (resolved instanceof PsiClass) return (PsiClass)resolved;
      }
      else if (qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        PsiType type = qualifier.getType();
        if (type instanceof PsiClassType) {
          return ((PsiClassType)type).resolve();
        }
      }
    }
    return null;
  }

  private static boolean processFieldUsages(@NotNull final PsiField field,
                                            @NotNull final JavaSliceUsage parent,
                                            @NotNull final PsiSubstitutor parentSubstitutor,
                                            @NotNull final Processor<SliceUsage> processor) {
    if (field.hasInitializer()) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null && !(field instanceof PsiCompiledElement)) {
        if (!handToProcessor(initializer, processor, parent, parentSubstitutor, parent.indexNesting, "")) return false;
      }
    }
    SearchScope searchScope = parent.getScope().toSearchScope();
    return ReferencesSearch.search(field, searchScope).forEach(reference -> {
      ProgressManager.checkCanceled();
      PsiElement element = reference.getElement();
      if (element instanceof PsiCompiledElement) {
        element = element.getNavigationElement();
        if (!parent.getScope().contains(element)) return true;
      }
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        PsiElement parentExpr = referenceExpression.getParent();
        if (PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
          PsiExpression rExpression = ((PsiAssignmentExpression)parentExpr).getRExpression();
          PsiType rtype = rExpression.getType();
          PsiType ftype = field.getType();
          PsiType subFType = parentSubstitutor.substitute(ftype);
          PsiType subRType = parentSubstitutor.substitute(rtype);
          if (subFType != null && subRType != null && TypeConversionUtil.isAssignable(subFType, subRType)) {
            return handToProcessor(rExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
          }
        }
        if (parentExpr instanceof PsiPrefixExpression && ((PsiPrefixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parentExpr;
          return handToProcessor(prefixExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
        }
        if (parentExpr instanceof PsiPostfixExpression && ((PsiPostfixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          PsiPostfixExpression postfixExpression = (PsiPostfixExpression)parentExpr;
          return handToProcessor(postfixExpression, processor, parent, parentSubstitutor, parent.indexNesting, "");
        }
      }

      return processIfInForeignLanguage(parent, parentSubstitutor, 0, "", processor, element);
    });
  }

  @NotNull
  static SliceUsage createSliceUsage(@NotNull PsiElement element,
                                     @NotNull SliceUsage parent,
                                     @NotNull PsiSubstitutor substitutor,
                                     int indexNesting,
                                     @NotNull String syntheticField) {
    return new JavaSliceUsage(simplify(element), parent, substitutor, indexNesting, syntheticField);
  }

  @NotNull
  private static SliceUsage createTooComplexDFAUsage(@NotNull PsiElement element, @NotNull SliceUsage parent) {
    return new SliceTooComplexDFAUsage(simplify(element), parent);
  }

  private static boolean processParameterUsages(@NotNull final PsiParameter parameter,
                                                @NotNull final SliceUsage parent,
                                                @NotNull final PsiSubstitutor parentSubstitutor,
                                                final int indexNesting,
                                                @NotNull final String syntheticField,
                                                @NotNull final Processor<SliceUsage> processor) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiForeachStatement) {
      PsiForeachStatement statement = (PsiForeachStatement)declarationScope;
      PsiExpression iterated = statement.getIteratedValue();
      if (statement.getIterationParameter() == parameter && iterated != null) {
        if (!handToProcessor(iterated, processor, parent, parentSubstitutor, indexNesting + 1, syntheticField)) return false;
      }
      return true;
    }
    if (!(declarationScope instanceof PsiMethod)) return true;

    final PsiMethod method = (PsiMethod)declarationScope;
    final PsiType actualParameterType = parameter.getType();

    final PsiParameter[] actualParameters = method.getParameterList().getParameters();
    final int paramSeqNo = ArrayUtilRt.find(actualParameters, parameter);
    assert paramSeqNo != -1;

    // first, check if we are looking for a specific method call.
    // it happens when we were processing that very same method() return values somewhere up the tree
    PsiCall specificMethodCall = findSpecificMethodCallUpTheTree(parent, method);
    if (specificMethodCall != null) {
      return processMethodCall(parent, parentSubstitutor, indexNesting, syntheticField, processor, actualParameterType, actualParameters,
                               paramSeqNo, specificMethodCall);
    }

    Collection<PsiMethod> superMethods = new THashSet<>(Arrays.asList(method.findDeepestSuperMethods()));
    superMethods.add(method);

    final Set<PsiReference> processed = new THashSet<>(); //usages of super method and overridden method can overlap
    for (final PsiMethod superMethod : superMethods) {
      if (!MethodReferencesSearch.search(superMethod, parent.getScope().toSearchScope(), true).forEach(reference -> {
        ProgressManager.checkCanceled();
        synchronized (processed) {
          if (!processed.add(reference)) return true;
        }
        PsiElement refElement = reference.getElement();
        return processMethodCall(parent, parentSubstitutor, indexNesting, syntheticField, processor, actualParameterType, actualParameters,
                                 paramSeqNo,
                                 refElement);
      })) {
        return false;
      }
    }

    return true;
  }

  private static PsiCall findSpecificMethodCallUpTheTree(SliceUsage parent, PsiMethod method) {
    while (parent != null) {
      PsiElement element = parent.getElement();
      if (element instanceof PsiCall && ((PsiCall)element).resolveMethod() == method) {
        return (PsiCall)element;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private static boolean processMethodCall(@NotNull SliceUsage parent,
                                           @NotNull PsiSubstitutor parentSubstitutor,
                                           int indexNesting,
                                           @NotNull String syntheticField,
                                           @NotNull Processor<SliceUsage> processor,
                                           PsiType actualParameterType,
                                           PsiParameter[] actualParameters,
                                           int paramSeqNo,
                                           PsiElement refElement) {
    PsiExpressionList argumentList;
    JavaResolveResult result;
    if (refElement instanceof PsiCall) {
      // the case of enum constant decl
      PsiCall call = (PsiCall)refElement;
      argumentList = call.getArgumentList();
      result = call.resolveMethodGenerics();
    }
    else {
      PsiElement element = refElement.getParent();
      if (element instanceof PsiCompiledElement) return true;
      if (element instanceof PsiAnonymousClass) {
        PsiAnonymousClass anon = (PsiAnonymousClass)element;
        argumentList = anon.getArgumentList();
        PsiElement callExp = element.getParent();
        if (!(callExp instanceof PsiCallExpression)) return true;
        result = ((PsiCall)callExp).resolveMethodGenerics();
      }
      else if (element instanceof PsiCall) {
          PsiCall call = (PsiCall)element;
          argumentList = call.getArgumentList();
          result = call.resolveMethodGenerics();
      }
      else {
        return processIfInForeignLanguage(parent, parentSubstitutor, indexNesting, syntheticField, processor, refElement);
      }
    }
    PsiSubstitutor substitutor = result.getSubstitutor();

    PsiExpression[] expressions = argumentList.getExpressions();
    if (paramSeqNo >= expressions.length) {
      return true;
    }
    PsiElement passExpression;
    PsiType actualExpressionType;
    if (actualParameterType instanceof PsiEllipsisType) {
      passExpression = argumentList;
      actualExpressionType = expressions[paramSeqNo].getType();
    }
    else {
      passExpression = expressions[paramSeqNo];
      actualExpressionType = ((PsiExpression)passExpression).getType();
    }

    Project project = argumentList.getProject();
    PsiElement element = result.getElement();
    if (element instanceof PsiCompiledElement) {
      element = element.getNavigationElement();
    }

    // for erased method calls for which we cannot determine target substitutor,
    // rely on call argument types. I.e. new Pair(1,2) -> Pair<Integer, Integer>
    if (element instanceof PsiTypeParameterListOwner && PsiUtil.isRawSubstitutor((PsiTypeParameterListOwner)element, substitutor)) {
      PsiTypeParameter[] typeParameters = substitutor.getSubstitutionMap().keySet().toArray(PsiTypeParameter.EMPTY_ARRAY);

      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(project).getResolveHelper();
      substitutor = resolveHelper.inferTypeArguments(typeParameters, actualParameters, expressions, parentSubstitutor, argumentList,
                                                     DefaultParameterTypeInferencePolicy.INSTANCE);
    }

    substitutor = removeRawMappingsLeftFromResolve(substitutor);

    PsiSubstitutor combined = unify(substitutor, parentSubstitutor, project);
    if (combined == null) return true;
    //PsiType substituted = combined.substitute(passExpression.getType());
    PsiType substituted = combined.substitute(actualExpressionType);
    if (substituted instanceof PsiPrimitiveType) {
      final PsiClassType boxedType = ((PsiPrimitiveType)substituted).getBoxedType(argumentList);
      substituted = boxedType != null ? boxedType : substituted;
    }
    if (substituted == null) return true;
    PsiType typeToCheck;
    if (actualParameterType instanceof PsiEllipsisType) {
      // there may be the case of passing the vararg argument to the other vararg method: foo(int... ints) { bar(ints); } bar(int... ints) {}
      if (TypeConversionUtil.areTypesConvertible(substituted, actualParameterType)) {
        return handToProcessor(expressions[paramSeqNo], processor, parent, combined, indexNesting, syntheticField);
      }
      typeToCheck = ((PsiEllipsisType)actualParameterType).getComponentType();
    }
    else {
      typeToCheck = actualParameterType;
    }
    if (!TypeConversionUtil.areTypesConvertible(substituted, typeToCheck)) return true;

    return handToProcessor(passExpression, processor, parent, combined, indexNesting, syntheticField);
  }

  private static boolean processIfInForeignLanguage(@NotNull SliceUsage parent,
                                                    @NotNull PsiSubstitutor parentSubstitutor,
                                                    int indexNesting,
                                                    @NotNull String syntheticField,
                                                    @NotNull Processor<SliceUsage> processor,
                                                    @NotNull PsiElement foreignElement) {
    PsiFile file = foreignElement.getContainingFile();
    if (file != null && file.getLanguage() != JavaLanguage.INSTANCE) {
      // show foreign language usage as leaf to warn about possible (but unknown to us) flow.
      if (!handToProcessor(foreignElement, processor, parent, parentSubstitutor, indexNesting, syntheticField)) {
        return false;
      }
    }
    return true;
  }

  private static void addContainerReferences(@NotNull PsiVariable variable,
                                             @NotNull final Processor<SliceUsage> processor,
                                             @NotNull final SliceUsage parent,
                                             @NotNull final PsiSubstitutor parentSubstitutor,
                                             final int indexNesting,
                                             @NotNull final String syntheticField) {
    if (indexNesting != 0) {
      ReferencesSearch.search(variable).forEach(reference -> {
        PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !element.getManager().areElementsEquivalent(element, parent.getElement())) {
          PsiExpression expression = (PsiExpression)element;
          if (!addContainerItemModification(expression, processor, parent, parentSubstitutor, indexNesting, syntheticField)) return false;
        }
        return true;
      });
    }
  }

  private static boolean addContainerItemModification(@NotNull PsiExpression expression,
                                                      @NotNull Processor<SliceUsage> processor,
                                                      @NotNull SliceUsage parent,
                                                      @NotNull PsiSubstitutor parentSubstitutor,
                                                      int indexNesting,
                                                      @NotNull String syntheticField) {
    PsiElement parentElement = expression.getParent();
    if (parentElement instanceof PsiArrayAccessExpression &&
        ((PsiArrayAccessExpression)parentElement).getArrayExpression() == expression &&
        PsiUtil.isAccessedForWriting((PsiExpression)parentElement)) {

      if (PsiUtil.isOnAssignmentLeftHand((PsiExpression)parentElement)) {
        PsiExpression rightSide = ((PsiAssignmentExpression)parentElement.getParent()).getRExpression();
        return rightSide == null || handToProcessor(rightSide, processor, parent, parentSubstitutor, indexNesting -1, syntheticField);
      }
    }
    PsiElement grand = parentElement == null ? null : parentElement.getParent();
    if (grand instanceof PsiCallExpression) {
      if (!processContainerPutArguments((PsiCallExpression)grand, parent, parentSubstitutor, indexNesting, syntheticField, processor)) return false;
    }
    return true;
  }

  private static boolean processContainerPutArguments(@NotNull PsiCallExpression call,
                                                      @NotNull SliceUsage parent,
                                                      @NotNull PsiSubstitutor parentSubstitutor,
                                                      int indexNesting,
                                                      @NotNull String syntheticField,
                                                      @NotNull Processor<SliceUsage> processor) {
    assert indexNesting != 0;
    JavaResolveResult result = call.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    if (method != null) {
      int parametersCount = method.getParameterList().getParametersCount();
      Flow[] annotations = new Flow[parametersCount];
      for (int i=0; i<parametersCount;i++) {
        annotations[i] = isParamFlowAnnotated(method, i);
      }

      PsiExpression[] expressions = call.getArgumentList().getExpressions();
      PsiParameter[] parameters = method.getParameterList().getParameters();
      for (int i = 0; i < expressions.length; i++) {
        PsiExpression argument = expressions[i];
        Flow anno;
        PsiParameter parameter;
        if (i>=parameters.length) {
          if (parameters.length != 0 && parameters[parameters.length-1].isVarArgs()) {
            anno = annotations[parameters.length - 1];
            parameter = parameters[parameters.length-1];
          }
          else {
            break;
          }
        }
        else {
          anno = annotations[i];
          parameter = parameters[i];
        }
        if (anno != null) {
          String target = anno.target();
          if (target.equals(Flow.DEFAULT_TARGET)) target = Flow.THIS_TARGET;
          if (target.startsWith(Flow.THIS_TARGET)) {
            String paramSynthetic = StringUtil.trimStart(StringUtil.trimStart(target, Flow.THIS_TARGET),".");
            if (paramSynthetic.equals(syntheticField)) {
              PsiSubstitutor substitutor = unify(result.getSubstitutor(), parentSubstitutor, argument.getProject());
              int nesting = calcNewIndexNesting(indexNesting, anno);
              if (!handToProcessor(argument, processor, parent, substitutor, nesting, paramSynthetic)) return false;
            }
          }
        }
        // check flow parameter to another param
        for (int si=0; si<annotations.length; si++) {
          if (si == i) continue;
          Flow sourceAnno = annotations[si];
          if (sourceAnno == null) continue;
          if (sourceAnno.target().equals(parameter.getName())) {
            int newNesting = calcNewIndexNesting(indexNesting, sourceAnno);
            PsiExpression sourceArgument = expressions[si];
            PsiSubstitutor substitutor = unify(result.getSubstitutor(), parentSubstitutor, argument.getProject());
            if (!handToProcessor(sourceArgument, processor, parent, substitutor, newNesting, syntheticField)) return false;
          }
        }
      }
    }
    return true;
  }

  private static int calcNewIndexNesting(int indexNesting, @NotNull Flow anno) {
    int nestingDelta = (anno.sourceIsContainer() ? 1 : 0) - (anno.targetIsContainer() ? 1 : 0);
    return indexNesting + nestingDelta;
  }

  @NotNull
  private static PsiSubstitutor removeRawMappingsLeftFromResolve(@NotNull PsiSubstitutor substitutor) {
    Map<PsiTypeParameter, PsiType> map = null;
    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      if (entry.getValue() == null) {
        if (map == null) map = new THashMap<>();
        map.put(entry.getKey(), entry.getValue());
      }
    }
    if (map == null) return substitutor;
    Map<PsiTypeParameter, PsiType> newMap = new THashMap<>(substitutor.getSubstitutionMap());
    newMap.keySet().removeAll(map.keySet());
    return PsiSubstitutorImpl.createSubstitutor(newMap);
  }

  @Nullable
  private static PsiSubstitutor unify(@NotNull PsiSubstitutor substitutor, @NotNull PsiSubstitutor parentSubstitutor, @NotNull Project project) {
    Map<PsiTypeParameter,PsiType> newMap = new THashMap<>(substitutor.getSubstitutionMap());

    for (Map.Entry<PsiTypeParameter, PsiType> entry : substitutor.getSubstitutionMap().entrySet()) {
      PsiTypeParameter typeParameter = entry.getKey();
      PsiType type = entry.getValue();
      PsiClass resolved = PsiUtil.resolveClassInType(type);
      if (!parentSubstitutor.getSubstitutionMap().containsKey(typeParameter)) continue;
      PsiType parentType = parentSubstitutor.substitute(parentSubstitutor.substitute(typeParameter));

      if (resolved instanceof PsiTypeParameter) {
        PsiTypeParameter res = (PsiTypeParameter)resolved;
        newMap.put(res, parentType);
      }
      else if (!Comparing.equal(type, parentType)) {
        return null; // cannot unify
      }
    }
    return JavaPsiFacade.getElementFactory(project).createSubstitutor(newMap);
  }
}
