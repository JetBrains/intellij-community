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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.resolve.graphInference.FunctionalInterfaceParameterizationUtil;
import com.intellij.psi.impl.source.tree.java.MethodReferenceResolver;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FunctionalExpressionCompletionProvider extends CompletionProvider<CompletionParameters> {
  static final Key<Boolean> LAMBDA_ITEM = Key.create("LAMBDA_ITEM");
  static final Key<Boolean> METHOD_REF_ITEM = Key.create("METHOD_REF_ITEM");

  private static boolean isLambdaContext(@NotNull PsiElement element) {
    final PsiElement rulezzRef = element.getParent();
    return rulezzRef instanceof PsiReferenceExpression &&
           ((PsiReferenceExpression)rulezzRef).getQualifier() == null &&
           LambdaUtil.isValidLambdaContext(rulezzRef.getParent());
  }

  static boolean isFunExprItem(LookupElement item) {
    return item.getUserData(LAMBDA_ITEM) != null || item.getUserData(METHOD_REF_ITEM) != null;
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                @NotNull ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    addFunctionalVariants(parameters, true, result.getPrefixMatcher(), result);
  }

  static void addFunctionalVariants(@NotNull CompletionParameters parameters, boolean addInheritors, PrefixMatcher matcher, Consumer<? super LookupElement> result) {
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
              LookupElementBuilder.create(functionalInterfaceMethod, paramsString + " -> ")
                .withPresentableText(paramsString + " -> {}")
                .withTypeText(functionalInterfaceType.getPresentableText())
                .withIcon(AllIcons.Nodes.Lambda);
            builder.putUserData(LAMBDA_ITEM, true);
            result.consume(builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
          }

          PsiType expectedReturnType = substitutor.substitute(functionalInterfaceMethod.getReturnType());
          if (expectedReturnType != null) {
            MethodReferenceCompletion completion =
              new MethodReferenceCompletion(addInheritors, parameters, matcher, functionalInterfaceType, params, originalPosition,
                                            substitutor, expectedReturnType);
            completion.suggestMethodReferences(element -> {
                element.putUserData(METHOD_REF_ITEM, true);
                result.consume(parameters.getCompletionType() == CompletionType.SMART
                               ? JavaSmartCompletionContributor.decorate(element, Arrays.asList(expectedTypes))
                               : element);
              }
            );
          }
        }
      }
    }
  }

  private static String getParamName(PsiParameter param, PsiElement originalPosition) {
    return JavaCodeStyleManager.getInstance(originalPosition.getProject()).suggestUniqueVariableName(
      ObjectUtils.assertNotNull(param.getName()), originalPosition, false);
  }

}

class MethodReferenceCompletion {
  private static final InsertHandler<LookupElement> CONSTRUCTOR_REF_INSERT_HANDLER = (context, item) -> {
    int start = context.getStartOffset();
    PsiClass psiClass = PsiUtil.resolveClassInType((PsiType)item.getObject());
    if (psiClass != null) {
      String insertedName = StringUtil.trimEnd(item.getLookupString(), "::new");
      while (insertedName.endsWith("[]")) insertedName = insertedName.substring(0, insertedName.length() - 2);
      JavaCompletionUtil.insertClassReference(psiClass, context.getFile(), start, start + insertedName.length());
    }
  };

  private final boolean myAddInheritors;
  private final CompletionParameters myParameters;
  private final PrefixMatcher myMatcher;
  private final PsiType myFunctionalInterfaceType;
  private final PsiParameter[] myParams;
  private final PsiElement myPosition;
  private final PsiSubstitutor mySubstitutor;
  private final PsiType myExpectedReturnType;

  MethodReferenceCompletion(boolean addInheritors, CompletionParameters parameters, PrefixMatcher matcher, PsiType functionalInterfaceType,
                            PsiParameter[] params, PsiElement originalPosition, PsiSubstitutor substitutor, PsiType expectedReturnType) {
    myAddInheritors = addInheritors;
    myParameters = parameters;
    myMatcher = matcher;
    myFunctionalInterfaceType = functionalInterfaceType;
    myParams = params;
    myPosition = originalPosition;
    mySubstitutor = substitutor;
    myExpectedReturnType = expectedReturnType;
  }

  void suggestMethodReferences(Consumer<? super LookupElement> result) {
    if (myParams.length > 0) {
      for (LookupElement element : collectVariantsByReceiver()) {
        result.consume(element);
      }
    }
    for (LookupElement element : collectThisVariants()) {
      result.consume(element);
    }

    for (LookupElement element : collectStaticVariants()) {
      result.consume(element);
    }

    Consumer<PsiType> consumer = eachReturnType -> {
      PsiClass psiClass = PsiUtil.resolveClassInType(eachReturnType);
      if (psiClass == null) return;

      if (eachReturnType.getArrayDimensions() == 0) {
        if (!MethodReferenceResolver.canBeConstructed(psiClass)) return;

        PsiMethod[] constructors = psiClass.getConstructors();
        for (PsiMethod psiMethod : constructors) {
          if (isSignatureAppropriate(psiMethod, 0, null)) {
            result.consume(createConstructorReferenceLookup(eachReturnType));
          }
        }
        if (constructors.length == 0 && myParams.length == 0) {
          result.consume(createConstructorReferenceLookup(eachReturnType));
        }
      }
      else if (myParams.length == 1 && PsiType.INT.equals(myParams[0].getType())) {
        result.consume(createConstructorReferenceLookup(eachReturnType));
      }
    };
    if (myAddInheritors && myExpectedReturnType instanceof PsiClassType) {
      JavaInheritorsGetter.processInheritors(myParameters, Collections.singletonList((PsiClassType)myExpectedReturnType), myMatcher, consumer);
    } else {
      consumer.consume(myExpectedReturnType);
    }
  }

  private LookupElement createConstructorReferenceLookup(@NotNull PsiType constructedType) {
    constructedType = TypeConversionUtil.erasure(constructedType);
    PsiClass psiClass = PsiUtil.resolveClassInType(constructedType);
    return LookupElementBuilder
      .create(constructedType, constructedType.getPresentableText() + "::new")
      .withTypeText(myFunctionalInterfaceType.getPresentableText())
      .withTailText(psiClass != null ? " (" + PsiFormatUtil.getPackageDisplayName(psiClass) + ")" : null, true)
      .withPsiElement(psiClass)
      .withIcon(AllIcons.Nodes.MethodReference)
      .withInsertHandler(CONSTRUCTOR_REF_INSERT_HANDLER)
      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
  }

  @NotNull
  private LookupElement createMethodRefOnThis(PsiMethod psiMethod, @Nullable PsiClass outerClass) {
    String fullString = (outerClass == null ? "" : outerClass.getName() + ".") + "this::" + psiMethod.getName();
    return LookupElementBuilder
      .create(psiMethod, fullString)
      .withLookupString(psiMethod.getName())
      .withPresentableText(fullString)
      .withTypeText(myFunctionalInterfaceType.getPresentableText())
      .withIcon(AllIcons.Nodes.MethodReference)
      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
  }

  @NotNull
  private LookupElement createMethodRefOnClass(PsiMethod psiMethod, PsiClass qualifierClass) {
    String presentableText = qualifierClass.getName() + "::" + psiMethod.getName();
    return LookupElementBuilder
      .create(psiMethod)
      .withLookupString(presentableText)
      .withPresentableText(presentableText)
      .withInsertHandler((context, item) -> {
        context.getDocument().insertString(context.getStartOffset(), "::");
        JavaCompletionUtil.insertClassReference(qualifierClass, context.getFile(), context.getStartOffset());
      })
      .withTypeText(myFunctionalInterfaceType.getPresentableText())
      .withIcon(AllIcons.Nodes.MethodReference)
      .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
  }

  private List<LookupElement> collectThisVariants() {
    List<LookupElement> result = new ArrayList<>();

    Iterable<PsiClass> instanceClasses = JBIterable
      .generate(myPosition, PsiElement::getParent)
      .filter(PsiMember.class)
      .takeWhile(m -> !m.hasModifierProperty(PsiModifier.STATIC))
      .filter(PsiClass.class);

    boolean first = true;
    for (PsiClass psiClass : instanceClasses) {
      if (!first && psiClass.getName() == null) continue;

      for (PsiMethod psiMethod : psiClass.getMethods()) {
        if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
            hasAppropriateReturnType(psiMethod) &&
            isSignatureAppropriate(psiMethod, 0, null)) {
          result.add(createMethodRefOnThis(psiMethod, first ? null : psiClass));
        }
      }
      first = false;
    }
    return result;
  }

  private List<LookupElement> collectStaticVariants() {
    List<LookupElement> result = new ArrayList<>();
    for (PsiClass psiClass : JBIterable.generate(PsiTreeUtil.getParentOfType(myPosition, PsiClass.class), PsiClass::getContainingClass)) {
      for (PsiMethod psiMethod : psiClass.getMethods()) {
        if (isMatchingStaticMethod(psiMethod)) {
          result.add(createMethodRefOnClass(psiMethod, psiClass));
        }
      }
    }

    PsiClass objects = JavaPsiFacade.getInstance(myPosition.getProject())
      .findClass(CommonClassNames.JAVA_UTIL_OBJECTS, myPosition.getResolveScope());
    if (objects != null) {
      for (PsiMethod nonNull : objects.getMethods()) {
        if (isMatchingStaticMethod(nonNull)) {
          result.add(createMethodRefOnClass(nonNull, objects));
        }
      }
    }

    return result;
  }

  private boolean isMatchingStaticMethod(PsiMethod psiMethod) {
    return psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
           hasAppropriateReturnType(psiMethod) &&
           isSignatureAppropriate(psiMethod, 0, null);
  }

  private List<LookupElement> collectVariantsByReceiver() {
    boolean prioritize = myParameters.getCompletionType() != CompletionType.SMART;
    List<LookupElement> result = new ArrayList<>();
    PsiType functionalInterfaceParamType = mySubstitutor.substitute(myParams[0].getType());
    PsiClass paramClass = PsiUtil.resolveClassInClassTypeOnly(functionalInterfaceParamType);
    if (paramClass != null) {
      final Set<String> visited = new HashSet<>();
      for (PsiMethod psiMethod : paramClass.getAllMethods()) {
        PsiClass containingClass = psiMethod.getContainingClass();
        PsiClass qualifierClass = containingClass != null ? containingClass : paramClass;
        if (!psiMethod.hasModifierProperty(PsiModifier.STATIC) &&
            hasAppropriateReturnType(psiMethod) &&
            isSignatureAppropriate(psiMethod, 1, paramClass) &&
            visited.add(psiMethod.getName())) {
          LookupElement methodRefLookupElement = createMethodRefOnClass(psiMethod, qualifierClass);
          if (prioritize && containingClass == paramClass) {
            methodRefLookupElement = PrioritizedLookupElement.withExplicitProximity(methodRefLookupElement, 1);
          }
          result.add(methodRefLookupElement);
        }
      }
    }
    return result;
  }

  private boolean hasAppropriateReturnType(PsiMethod psiMethod) {
    PsiType returnType = psiMethod.getReturnType();
    return returnType != null && TypeConversionUtil.isAssignable(myExpectedReturnType, mySubstitutor.substitute(returnType));
  }

  private boolean isSignatureAppropriate(PsiMethod psiMethod, int offset, PsiClass accessObjectClass) {
    if (!PsiUtil.isAccessible(psiMethod, myPosition, accessObjectClass)) return false;

    PsiParameterList parameterList = psiMethod.getParameterList();
    if (parameterList.getParametersCount() == myParams.length - offset) {
      final PsiParameter[] referenceMethodParams = parameterList.getParameters();
      for (int i = 0; i < myParams.length - offset; i++) {
        if (!TypeConversionUtil.isAssignable(referenceMethodParams[i].getType(), mySubstitutor.substitute(myParams[i + offset].getType()))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
