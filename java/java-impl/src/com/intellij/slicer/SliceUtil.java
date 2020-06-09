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

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class SliceUtil {
  static boolean processUsagesFlownDownTo(@NotNull PsiElement expression,
                                          @NotNull Processor<? super SliceUsage> processor,
                                          @NotNull JavaSliceBuilder builder) {
    expression = JavaSliceUsage.simplify(expression);
    PsiElement original = expression;
    if (expression instanceof PsiArrayAccessExpression) {
      // now start tracking the array instead of element
      expression = ((PsiArrayAccessExpression)expression).getArrayExpression();
      builder = builder.incrementNesting();
    }
    PsiElement par = expression.getParent();
    if (expression instanceof PsiExpressionList && par instanceof PsiMethodCallExpression) {
      // expression list ends up here if we track varargs
      PsiMethod method = ((PsiMethodCallExpression)par).resolveMethod();
      if (method != null) {
        int parametersCount = method.getParameterList().getParametersCount();
        if (parametersCount != 0) {
          // unfold varargs list into individual expressions
          PsiExpression[] expressions = ((PsiExpressionList)expression).getExpressions();
          if (builder.hasNesting()) {
            // should skip not-vararg arguments
            for (int i = parametersCount-1; i < expressions.length; i++) {
              PsiExpression arg = expressions[i];
              if (!builder.decrementNesting().process(arg, processor)) return false;
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
        return rightSide == null || builder.process(rightSide, processor);
      }
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      JavaResolveResult result = ref.advancedResolve(false);
      builder = builder.withSubstitutor(result.getSubstitutor().putAll(builder.getSubstitutor()));
      PsiElement resolved = result.getElement();
      if (resolved instanceof PsiCompiledElement) {
        resolved = resolved.getNavigationElement();
      }
      if (resolved instanceof PsiMethod && expression.getParent() instanceof PsiMethodCallExpression) {
        return processUsagesFlownDownTo(expression.getParent(), processor, builder);
      }
      if (!(resolved instanceof PsiVariable)) return true;
      // check for container item modifications, like "array[i] = expression;"
      addContainerReferences((PsiVariable)resolved, processor, builder);

      needToReportDeclaration = true;
      if (resolved instanceof PsiField || StackFilter.getElementContext(resolved) != StackFilter.getElementContext(expression)) {
        builder = builder.withFilter(JavaValueFilter::dropFrameFilter);
      }
      expression = resolved;
    }
    if (expression instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)expression;
      Collection<PsiExpression> values = DfaUtil.getVariableValues(variable, original);
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
        if (!builder.process(exp, processor)) return false;
        if (exp == initializer) initializerReported = true;
      }

      if (!initializerReported && needToReportDeclaration) { // otherwise we'll reach var declaration anyway because it is the initializer' parent
        // parameter or variable declaration can be far away from its usage (except for variable initializer) so report it separately
        return builder.process(variable, processor);
      }

      if (variable instanceof PsiField) {
        return processFieldUsages((PsiField)variable, builder.dropSyntheticField(), processor);
      }
      else if (variable instanceof PsiParameter) {
        return processParameterUsages((PsiParameter)variable, builder.withFilter(f -> f.popFrame(variable.getProject())), processor);
      }
    }
    if (expression instanceof PsiMethodCallExpression) { // ctr call can't return value or be container get, so don't use PsiCall here
      PsiExpression returnedValue = JavaMethodContractUtil.findReturnedValue((PsiMethodCallExpression)expression);
      if (returnedValue != null) {
        if (!builder.process(returnedValue, processor)) {
          return false;
        }
      }
      PsiMethod method = ((PsiMethodCallExpression)expression).resolveMethod();
      Flow anno = method == null ? null : isMethodFlowAnnotated(method);
      if (anno != null) {
        String target = anno.target();
        if (target.equals(Flow.DEFAULT_TARGET)) target = Flow.RETURN_METHOD_TARGET;
        if (target.equals(Flow.RETURN_METHOD_TARGET)) {
          PsiExpression qualifier = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
          if (qualifier != null) {
            builder = builder.updateNesting(anno);
            String source = anno.source();
            if (source.equals(Flow.DEFAULT_SOURCE)) source = Flow.THIS_SOURCE;
            String synthetic = StringUtil.trimStart(StringUtil.trimStart(source, Flow.THIS_SOURCE),".");
            return processUsagesFlownDownTo(qualifier, processor, builder.withSyntheticField(synthetic));
          }
        }
      }
      return processMethodReturnValue((PsiMethodCallExpression)expression, processor, builder);
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      PsiExpression thenE = conditional.getThenExpression();
      PsiExpression elseE = conditional.getElseExpression();
      if (thenE != null && !builder.process(thenE, processor)) return false;
      if (elseE != null && !builder.process(elseE, processor)) return false;
    }
    if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
      IElementType tokenType = assignment.getOperationTokenType();
      PsiExpression rExpression = assignment.getRExpression();

      if (tokenType == JavaTokenType.EQ && rExpression != null) {
        return processUsagesFlownDownTo(rExpression, processor, builder);
      }
    }
    DfType filterDfType = builder.getFilter().getDfType();
    if (filterDfType != DfTypes.TOP && expression instanceof PsiExpression) {
      AnalysisStartingPoint analysis = AnalysisStartingPoint.propagateThroughExpression(expression, filterDfType);
      if (analysis != null) {
        return builder.withFilter(filter -> filter.withType(analysis.myDfType)).process(analysis.myAnchor, processor);
      }
    }
    
    if (builder.hasNesting()) {
      // consider container creation
      PsiElement initializer = expression instanceof PsiNewExpression ? ((PsiNewExpression)expression).getArrayInitializer() : expression;
      if (initializer instanceof PsiArrayInitializerExpression) {
        for (PsiExpression init : ((PsiArrayInitializerExpression)initializer).getInitializers()) {
          if (!builder.decrementNesting().process(init, processor)) return false;
        }
      }

      // check for constructor put arguments
      return !(expression instanceof PsiNewExpression) ||
             processContainerPutArguments((PsiNewExpression)expression, builder, processor);
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

  @NotNull
  static Collection<SliceUsage> collectMethodReturnValues(@NotNull SliceUsage parent,
                                                          @NotNull PsiSubstitutor parentSubstitutor,
                                                          PsiMethod methodCalled) {
    CommonProcessors.CollectProcessor<SliceUsage> processor = new CommonProcessors.CollectProcessor<>();
    processMethodReturnValue(processor, parentSubstitutor, null, methodCalled, null,
                             JavaSliceBuilder.create(parent).withSubstitutor(PsiSubstitutor.EMPTY).dropSyntheticField());
    return processor.getResults();
  }

  private static boolean processMethodReturnValue(@NotNull final PsiMethodCallExpression methodCallExpr,
                                                  @NotNull final Processor<? super SliceUsage> processor,
                                                  @NotNull final JavaSliceBuilder builder) {
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

    final PsiType parentType = builder.substitute(methodCallExpr.getType());
    final PsiSubstitutor substitutor = resolved.getSubstitutor().putAll(builder.getSubstitutor());
    return processMethodReturnValue(processor, builder.getSubstitutor(), qualifierClass, methodCalled, parentType,
                                    builder.withSubstitutor(substitutor).dropSyntheticField().withFilter(JavaValueFilter::pushFrame));
  }

  private static boolean processMethodReturnValue(@NotNull Processor<? super SliceUsage> processor,
                                                  @NotNull PsiSubstitutor parentSubstitutor,
                                                  @Nullable PsiClass qualifierClass,
                                                  PsiMethod methodCalled,
                                                  @Nullable PsiType parentType,
                                                  @NotNull JavaSliceBuilder builder) {
    Collection<PsiMethod> overrides = new THashSet<>();
    SearchScope scope = builder.getSearchScope();
    if (qualifierClass != null && qualifierClass != methodCalled.getContainingClass()) {
      scope = JavaTargetElementEvaluator.getHierarchyScope(qualifierClass, scope);
    }
    overrides.addAll(OverridingMethodsSearch.search(methodCalled, scope, true).findAll());
    overrides.add(methodCalled);

    final boolean[] result = {true};
    for (PsiMethod override : overrides) {
      if (!result[0]) break;
      if (override instanceof PsiCompiledElement) {
        override = (PsiMethod)override.getNavigationElement();
      }
      if (!builder.getParent().getScope().contains(PsiUtil.preferCompiledElement(override))) continue;

      Language language = override.getLanguage();
      if (language != JavaLanguage.INSTANCE) {
        builder.process(override, processor);
        continue;
      }

      final PsiCodeBlock body = override.getBody();
      if (body == null) continue;

      PsiSubstitutor origSubstitutor = builder.getSubstitutor();
      final PsiSubstitutor s = methodCalled == override ? origSubstitutor :
                               MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodCalled.getSignature(origSubstitutor), 
                                                                                      override.getSignature(origSubstitutor));
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
          if (right == null || parentType != null && !TypeConversionUtil.isAssignable(parentType, right)) return;
          if (!builder.process(returnValue, processor)) {
            stopWalking();
            result[0] = false;
          }
        }
      });
    }

    return result[0];
  }

  private static PsiClass resolveQualifier(@NotNull PsiMethodCallExpression expr) {
    PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(expr.getMethodExpression());
    if (qualifier == null) return null;
    PsiType psiType = null;
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(qualifier);
    if (result != null) {
      psiType = TypeConstraint.fromDfType(result.getDfTypeNoAssertions(qualifier)).getPsiType(qualifier.getProject());
    }
    if (psiType == null) {
      psiType = qualifier.getType();
    }
    return PsiUtil.resolveClassInClassTypeOnly(psiType);
  }

  private static boolean processFieldUsages(@NotNull final PsiField field,
                                            @NotNull final JavaSliceBuilder builder,
                                            @NotNull final Processor<? super SliceUsage> processor) {
    if (field.hasInitializer()) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null && !(field instanceof PsiCompiledElement)) {
        if (!builder.process(initializer, processor)) return false;
      }
    }
    AnalysisScope scope = builder.getParent().getScope();
    SearchScope searchScope = builder.getSearchScope();
    return ReferencesSearch.search(field, searchScope).forEach(reference -> {
      ProgressManager.checkCanceled();
      PsiElement element = reference.getElement();
      if (element instanceof PsiCompiledElement) {
        element = element.getNavigationElement();
        if (!scope.contains(element)) return true;
      }
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        PsiElement parentExpr = referenceExpression.getParent();
        if (PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
          PsiExpression rExpression = ((PsiAssignmentExpression)parentExpr).getRExpression();
          if (rExpression != null) {
            PsiType rType = rExpression.getType();
            PsiType fType = field.getType();
            PsiType subFType = builder.substitute(fType);
            PsiType subRType = builder.substitute(rType);
            if (subFType != null && subRType != null && TypeConversionUtil.isAssignable(subFType, subRType)) {
              return builder.process(rExpression, processor);
            }
          }
        }
        if (parentExpr instanceof PsiUnaryExpression && ((PsiUnaryExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiUnaryExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiUnaryExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          PsiUnaryExpression unaryExpression = (PsiUnaryExpression)parentExpr;
          return builder.process(unaryExpression, processor);
        }
      }

      return processIfInForeignLanguage(builder.dropNesting(), processor, element);
    });
  }

  private static boolean processParameterUsages(@NotNull final PsiParameter parameter,
                                                @NotNull final JavaSliceBuilder builder,
                                                @NotNull final Processor<? super SliceUsage> processor) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (declarationScope instanceof PsiForeachStatement) {
      PsiForeachStatement statement = (PsiForeachStatement)declarationScope;
      PsiExpression iterated = statement.getIteratedValue();
      return statement.getIterationParameter() != parameter ||
             iterated == null ||
             builder.incrementNesting().process(iterated, processor);
    }
    if (!(declarationScope instanceof PsiMethod)) return true;

    final PsiMethod method = (PsiMethod)declarationScope;
    final PsiType actualParameterType = parameter.getType();

    final PsiParameter[] actualParameters = method.getParameterList().getParameters();
    final int paramSeqNo = ArrayUtilRt.find(actualParameters, parameter);
    assert paramSeqNo != -1;

    // first, check if we are looking for a specific method call.
    // it happens when we were processing that very same method() return values somewhere up the tree
    SliceUsage specificMethodCall = findSpecificMethodCallUpTheTree(builder.getParent(), method);
    if (specificMethodCall != null) {
      SliceValueFilter filter = specificMethodCall.params.valueFilter;
      return processMethodCall(builder.withFilter(f -> f.copyStackFrom(filter)), 
                               processor, actualParameterType, actualParameters, paramSeqNo, specificMethodCall.getElement());
    }

    Collection<PsiMethod> superMethods = ContainerUtil.set(method.findDeepestSuperMethods());
    superMethods.add(method);

    final Set<PsiReference> processed = new THashSet<>(); //usages of super method and overridden method can overlap
    for (final PsiMethod superMethod : superMethods) {
      if (!MethodReferencesSearch.search(superMethod, builder.getSearchScope(), true).forEach(reference -> {
        ProgressManager.checkCanceled();
        synchronized (processed) {
          if (!processed.add(reference)) return true;
        }
        PsiElement refElement = reference.getElement();
        return processMethodCall(builder, processor, actualParameterType, actualParameters, paramSeqNo, refElement);
      })) {
        return false;
      }
    }

    return true;
  }

  private static SliceUsage findSpecificMethodCallUpTheTree(SliceUsage parent, PsiMethod method) {
    while (parent != null) {
      PsiElement element = parent.getElement();
      if (element instanceof PsiCall && method.getManager().areElementsEquivalent(((PsiCall)element).resolveMethod(), method)) {
        return parent;
      }
      parent = parent.getParent();
    }
    return null;
  }

  private static boolean processMethodCall(@NotNull JavaSliceBuilder builder,
                                           @NotNull Processor<? super SliceUsage> processor,
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
        return processIfInForeignLanguage(builder, processor, refElement);
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
      substitutor = resolveHelper.inferTypeArguments(typeParameters, actualParameters, expressions, builder.getSubstitutor(), argumentList,
                                                     DefaultParameterTypeInferencePolicy.INSTANCE);
    }

    substitutor = removeRawMappingsLeftFromResolve(substitutor);

    builder = builder.combineSubstitutor(substitutor, project);
    if (builder == null) return true;
    PsiType substituted = builder.substitute(actualExpressionType);
    if (substituted instanceof PsiPrimitiveType) {
      final PsiClassType boxedType = ((PsiPrimitiveType)substituted).getBoxedType(argumentList);
      substituted = boxedType != null ? boxedType : substituted;
    }
    if (substituted == null) return true;
    PsiType typeToCheck;
    if (actualParameterType instanceof PsiEllipsisType) {
      // there may be the case of passing the vararg argument to the other vararg method: foo(int... ints) { bar(ints); } bar(int... ints) {}
      if (TypeConversionUtil.areTypesConvertible(substituted, actualParameterType)) {
        return builder.process(expressions[paramSeqNo], processor);
      }
      typeToCheck = ((PsiEllipsisType)actualParameterType).getComponentType();
    }
    else {
      typeToCheck = actualParameterType;
    }
    if (!TypeConversionUtil.areTypesConvertible(substituted, typeToCheck)) return true;

    return builder.process(passExpression, processor);
  }

  private static boolean processIfInForeignLanguage(@NotNull JavaSliceBuilder builder,
                                                    @NotNull Processor<? super SliceUsage> processor,
                                                    @NotNull PsiElement foreignElement) {
    PsiFile file = foreignElement.getContainingFile();
    if (file != null && file.getLanguage() != JavaLanguage.INSTANCE) {
      // show foreign language usage as leaf to warn about possible (but unknown to us) flow.
      return builder.process(foreignElement, processor);
    }
    return true;
  }

  private static void addContainerReferences(@NotNull PsiVariable variable,
                                             @NotNull final Processor<? super SliceUsage> processor,
                                             @NotNull final JavaSliceBuilder builder) {
    if (builder.hasNesting()) {
      ReferencesSearch.search(variable).forEach(reference -> {
        PsiElement element = reference.getElement();
        if (element instanceof PsiExpression && !element.getManager().areElementsEquivalent(element, builder.getParent().getElement())) {
          PsiExpression expression = (PsiExpression)element;
          return addContainerItemModification(expression, processor, builder);
        }
        return true;
      });
    }
  }

  private static boolean addContainerItemModification(@NotNull PsiExpression expression,
                                                      @NotNull Processor<? super SliceUsage> processor,
                                                      @NotNull JavaSliceBuilder builder) {
    PsiElement parentElement = expression.getParent();
    if (parentElement instanceof PsiArrayAccessExpression &&
        ((PsiArrayAccessExpression)parentElement).getArrayExpression() == expression &&
        PsiUtil.isAccessedForWriting((PsiExpression)parentElement)) {

      if (PsiUtil.isOnAssignmentLeftHand((PsiExpression)parentElement)) {
        PsiExpression rightSide = ((PsiAssignmentExpression)parentElement.getParent()).getRExpression();
        return rightSide == null || builder.decrementNesting().process(rightSide, processor);
      }
    }
    PsiElement grand = parentElement == null ? null : parentElement.getParent();
    if (grand instanceof PsiCallExpression) {
      return processContainerPutArguments((PsiCallExpression)grand, builder, processor);
    }
    return true;
  }

  private static boolean processContainerPutArguments(@NotNull PsiCallExpression call,
                                                      @NotNull JavaSliceBuilder builder,
                                                      @NotNull Processor<? super SliceUsage> processor) {
    assert builder.hasNesting();
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
            if (paramSynthetic.equals(builder.getSyntheticField())) {
              JavaSliceBuilder combined = builder.updateNesting(anno).combineSubstitutor(result.getSubstitutor(), argument.getProject());
              if (combined != null && !combined.process(argument, processor)) return false;
            }
          }
        }
        // check flow parameter to another param
        for (int si=0; si<annotations.length; si++) {
          if (si == i) continue;
          Flow sourceAnno = annotations[si];
          if (sourceAnno == null) continue;
          if (sourceAnno.target().equals(parameter.getName())) {
            PsiExpression sourceArgument = expressions[si];
            JavaSliceBuilder combined = builder.updateNesting(sourceAnno).combineSubstitutor(result.getSubstitutor(), argument.getProject());
            if (combined != null && !combined.process(sourceArgument, processor)) return false;
          }
        }
      }
    }
    return true;
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
    return PsiSubstitutor.createSubstitutor(newMap);
  }
}
