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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.tree.java.MethodReferenceResolver;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * User: anna
 */
public class FunctionalExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {

  private static final InsertHandler<LookupElement> CONSTRUCTOR_REF_INSERT_HANDLER = (context, item) -> {
    int start = context.getStartOffset();
    PsiClass psiClass = PsiUtil.resolveClassInType((PsiType)item.getObject());
    if (psiClass != null) {
      JavaCompletionUtil.insertClassReference(psiClass, context.getFile(), start,
                                              start + StringUtil.trimEnd(item.getLookupString(), "::new").length());
    }
  };

  private static boolean isLambdaContext(@NotNull PsiElement element) {
    final PsiElement rulezzRef = element.getParent();
    return rulezzRef != null &&
           rulezzRef instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)rulezzRef).getQualifier() == null &&
           LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    addFunctionalVariants(parameters, true, true, result);
  }

  static void addFunctionalVariants(@NotNull CompletionParameters parameters, boolean smart, boolean addInheritors, CompletionResultSet result) {
    if (!PsiUtil.isLanguageLevel8OrHigher(parameters.getOriginalFile()) || !isLambdaContext(parameters.getPosition())) return;

    ExpectedTypeInfo[] expectedTypes = JavaSmartCompletionContributor.getExpectedTypes(parameters);
    for (ExpectedTypeInfo expectedType : expectedTypes) {
      final PsiType defaultType = expectedType.getDefaultType();
      if (LambdaUtil.isFunctionalType(defaultType)) {
        final PsiType functionalInterfaceType = FunctionalInterfaceParameterizationUtil.getGroundTargetType(defaultType);
        final PsiMethod functionalInterfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(functionalInterfaceType);
        if (functionalInterfaceMethod != null) {
          PsiParameter[] params = PsiParameter.EMPTY_ARRAY;
          final PsiElement originalPosition = parameters.getPosition();
          final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(functionalInterfaceMethod, PsiUtil.resolveGenericsClassInType(functionalInterfaceType));
          if (!functionalInterfaceMethod.hasTypeParameters()) {
            params = functionalInterfaceMethod.getParameterList().getParameters();
            final Project project = functionalInterfaceMethod.getProject();
            final JVMElementFactory jvmElementFactory = JVMElementFactories.getFactory(originalPosition.getLanguage(), project);
            final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
            if (jvmElementFactory != null) {
              params = GenerateMembersUtil.overriddenParameters(params, jvmElementFactory, javaCodeStyleManager, substitutor, originalPosition);
            }

            String paramsString =
              params.length == 1 ? getParamName(params[0], originalPosition)
                                 : "(" + StringUtil.join(params, parameter -> getParamName(parameter, originalPosition), ",") + ")";

            final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiLambdaExpression lambdaExpression = (PsiLambdaExpression)JavaPsiFacade.getElementFactory(project)
              .createExpressionFromText(paramsString + " -> {}", null);
            lambdaExpression = (PsiLambdaExpression)codeStyleManager.reformat(lambdaExpression);
            paramsString = lambdaExpression.getParameterList().getText();
            final LookupElementBuilder builder =
              LookupElementBuilder.create(functionalInterfaceMethod, paramsString)
                .withPresentableText(paramsString + " -> {}")
                .withInsertHandler((context, item) -> EditorModificationUtil.insertStringAtCaret(context.getEditor(), " -> "))
                .withTypeText(functionalInterfaceType.getPresentableText())
                .withIcon(AllIcons.Nodes.Function);
            LookupElement lambdaElement = builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
            result.addElement(smart ? lambdaElement : PrioritizedLookupElement.withPriority(lambdaElement, 1));
          }

          addMethodReferenceVariants(
            smart, addInheritors, parameters, result.getPrefixMatcher(), functionalInterfaceType, functionalInterfaceMethod, params, originalPosition, substitutor,
            element -> result.addElement(smart ? JavaSmartCompletionContributor.decorate(element, Arrays.asList(expectedTypes)) : element));
        }
      }
    }
  }

  private static void addMethodReferenceVariants(boolean smart,
                                                 boolean addInheritors,
                                                 CompletionParameters parameters,
                                                 PrefixMatcher matcher,
                                                 PsiType functionalInterfaceType,
                                                 PsiMethod functionalInterfaceMethod,
                                                 PsiParameter[] params,
                                                 PsiElement originalPosition,
                                                 PsiSubstitutor substitutor,
                                                 Consumer<LookupElement> result) {
    final PsiType expectedReturnType = substitutor.substitute(functionalInterfaceMethod.getReturnType());
    if (expectedReturnType == null) return;

    if (params.length > 0) {
      for (LookupElement element : collectVariantsByReceiver(!smart, functionalInterfaceType, params, originalPosition, substitutor, expectedReturnType)) {
        result.consume(element);
      }
    }
    for (LookupElement element : collectThisVariants(functionalInterfaceType, params, originalPosition, substitutor, expectedReturnType)) {
      result.consume(element);
    }

    Consumer<PsiType> consumer = eachReturnType -> {
      PsiClass psiClass = PsiUtil.resolveClassInType(eachReturnType);
      if (psiClass == null || psiClass instanceof PsiTypeParameter) return;

      if (eachReturnType.getArrayDimensions() == 0) {
        PsiMethod[] constructors = psiClass.getConstructors();
        for (PsiMethod psiMethod : constructors) {
          if (areParameterTypesAppropriate(psiMethod, params, substitutor, 0)) {
            result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
          }
        }
        if (constructors.length == 0 && params.length == 0 && MethodReferenceResolver.canBeConstructed(psiClass)) {
          result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
        }
      }
      else if (params.length == 1 && PsiType.INT.equals(params[0].getType())) {
        result.consume(createConstructorReferenceLookup(functionalInterfaceType, eachReturnType));
      }
    };
    if (addInheritors && expectedReturnType instanceof PsiClassType) {
      JavaInheritorsGetter.processInheritors(parameters, Collections.singletonList((PsiClassType)expectedReturnType), matcher, consumer);
    } else {
      consumer.consume(expectedReturnType);
    }
  }

  private static LookupElement createConstructorReferenceLookup(@NotNull PsiType functionalInterfaceType, 
                                                                @NotNull PsiType constructedType) {
    constructedType = TypeConversionUtil.erasure(constructedType);
    return LookupElementBuilder
                      .create(constructedType, constructedType.getPresentableText() + "::new")
                      .withTypeText(functionalInterfaceType.getPresentableText())
                      .withIcon(AllIcons.Nodes.MethodReference)
                      .withInsertHandler(CONSTRUCTOR_REF_INSERT_HANDLER)
                      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
  }

  private static List<LookupElement> collectThisVariants(PsiType functionalInterfaceType,
                                                         PsiParameter[] params,
                                                         PsiElement originalPosition,
                                                         PsiSubstitutor substitutor, PsiType expectedReturnType) {
    List<LookupElement> result = new ArrayList<>();
    final PsiClass psiClass = PsiTreeUtil.getParentOfType(originalPosition, PsiClass.class);
    if (psiClass != null) {
      for (PsiMethod psiMethod : psiClass.getMethods()) {
        final PsiType returnType = psiMethod.getReturnType();
        if (isInstanceMethodWithAppropriateReturnType(expectedReturnType, psiMethod, returnType) &&
            areParameterTypesAppropriate(psiMethod, params, substitutor, 0)) {
          LookupElement methodRefLookupElement = LookupElementBuilder
            .create(psiMethod)
            .withPresentableText("this::" + psiMethod.getName())
            .withInsertHandler((context, item) -> context.getDocument().insertString(context.getStartOffset(), "this::"))
            .withTypeText(functionalInterfaceType.getPresentableText())
            .withIcon(AllIcons.Nodes.MethodReference)
            .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          result.add(methodRefLookupElement);
        }
      }
    }
    return result;
  }

  private static List<LookupElement> collectVariantsByReceiver(boolean prioritize,
                                                               PsiType functionalInterfaceType,
                                                               PsiParameter[] params,
                                                               PsiElement originalPosition,
                                                               PsiSubstitutor substitutor,
                                                               PsiType expectedReturnType) {
    List<LookupElement> result = new ArrayList<>();
    final PsiType functionalInterfaceParamType = substitutor.substitute(params[0].getType());
    final PsiClass paramClass = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceParamType);
    if (paramClass != null && !paramClass.hasTypeParameters()) {
      final Set<String> visited = new HashSet<>();
      for (PsiMethod psiMethod : paramClass.getAllMethods()) {
        final PsiType returnType = psiMethod.getReturnType();
        PsiClass containingClass = psiMethod.getContainingClass();
        PsiClass qualifierClass = containingClass != null ? containingClass : paramClass;
        if (visited.add(psiMethod.getName()) &&
            isInstanceMethodWithAppropriateReturnType(expectedReturnType, psiMethod, returnType) &&
            areParameterTypesAppropriate(psiMethod, params, substitutor, 1) &&
            JavaResolveUtil.isAccessible(psiMethod, null, psiMethod.getModifierList(), originalPosition, null, null)) {
          LookupElement methodRefLookupElement = LookupElementBuilder
            .create(psiMethod)
            .withPresentableText(qualifierClass.getName() + "::" + psiMethod.getName())
            .withInsertHandler((context, item) -> {
              int startOffset = context.getStartOffset();
              context.getDocument().insertString(startOffset, "::");
              JavaCompletionUtil.insertClassReference(qualifierClass, context.getFile(), startOffset);
            })
            .withTypeText(functionalInterfaceType.getPresentableText())
            .withIcon(AllIcons.Nodes.MethodReference)
            .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          if (prioritize && containingClass == paramClass) {
            methodRefLookupElement = PrioritizedLookupElement.withPriority(methodRefLookupElement, 1);
          }
          result.add(methodRefLookupElement);
        }
      }
    }
    return result;
  }

  private static boolean isInstanceMethodWithAppropriateReturnType(PsiType expectedReturnType, PsiMethod psiMethod, PsiType returnType) {
    return returnType != null &&
           !psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
           TypeConversionUtil.isAssignable(expectedReturnType, returnType);
  }

  private static boolean areParameterTypesAppropriate(PsiMethod psiMethod, PsiParameter[] params, PsiSubstitutor substitutor, int offset) {
    final PsiParameterList parameterList = psiMethod.getParameterList();
    if (parameterList.getParametersCount() == params.length - offset) {
      final PsiParameter[] referenceMethodParams = parameterList.getParameters();
      for (int i = 0; i < params.length - offset; i++) {
        if (!Comparing.equal(referenceMethodParams[i].getType(), substitutor.substitute(params[i + offset].getType()))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static String getParamName(PsiParameter param, PsiElement originalPosition) {
    return JavaCodeStyleManager.getInstance(originalPosition.getProject()).suggestUniqueVariableName(
      ObjectUtils.assertNotNull(param.getName()), originalPosition, false);
  }
}
