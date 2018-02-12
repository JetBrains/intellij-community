/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightClassUtil;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaInheritorsGetter extends CompletionProvider<CompletionParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.JavaInheritorsGetter");
  private final ConstructorInsertHandler myConstructorInsertHandler;

  public JavaInheritorsGetter(final ConstructorInsertHandler constructorInsertHandler) {
    myConstructorInsertHandler = constructorInsertHandler;
  }

  private static boolean shouldAddArrayInitializer(PsiElement position) {
    if (!JavaCompletionContributor.isInJavaContext(position) || !JavaSmartCompletionContributor.AFTER_NEW.accepts(position)) {
      return false;
    }
    PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(position, PsiNewExpression.class);
    return newExpression != null && newExpression.getParent() instanceof PsiExpressionList;
  }

  @Override
  public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final ExpectedTypeInfo[] infos = JavaSmartCompletionContributor.getExpectedTypes(parameters);

    final List<ExpectedTypeInfo> infoCollection = Arrays.asList(infos);
    generateVariants(parameters, result.getPrefixMatcher(), infos,
                     lookupElement -> result.addElement(JavaSmartCompletionContributor.decorate(lookupElement, infoCollection)));
  }

  public void generateVariants(final CompletionParameters parameters, final PrefixMatcher prefixMatcher, final Consumer<LookupElement> consumer) {
    generateVariants(parameters, prefixMatcher, JavaSmartCompletionContributor.getExpectedTypes(parameters), consumer);
  }

  private void generateVariants(final CompletionParameters parameters, final PrefixMatcher prefixMatcher,
                                final ExpectedTypeInfo[] infos, final Consumer<LookupElement> consumer) {

    addArrayTypes(parameters.getPosition(), infos, consumer);

    List<PsiClassType> classTypes = extractClassTypes(infos);
    boolean arraysWelcome = ContainerUtil.exists(ExpectedTypesGetter.extractTypes(infos, true),
                                                 t -> t.getDeepComponentType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT));
    processInheritors(parameters, classTypes, prefixMatcher, type -> {
      final LookupElement element = addExpectedType(type, parameters);
      if (element != null) {
        Supplier<PsiClassType> itemType = () -> (PsiClassType)ObjectUtils.assertNotNull(element.as(TypedLookupItem.CLASS_CONDITION_KEY)).getType();
        JavaConstructorCallElement.wrap(element, (PsiClass)element.getObject(), parameters.getPosition(), itemType).forEach(consumer::consume);
      }
      if (arraysWelcome) {
        consumer.consume(createNewArrayItem(parameters.getPosition(), type.createArrayType()));
      }
    });
  }

  private static void addArrayTypes(PsiElement identifierCopy,
                                    ExpectedTypeInfo[] infos, final Consumer<LookupElement> consumer) {

    for (final PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiArrayType) {
        consumer.consume(createNewArrayItem(identifierCopy, type));

        if (shouldAddArrayInitializer(identifierCopy)) {
          PsiTypeLookupItem item = createNewArrayItem(identifierCopy, type);
          item.setAddArrayInitializer();
          consumer.consume(item);
        }
      }
    }
  }

  private static PsiTypeLookupItem createNewArrayItem(PsiElement context, PsiType type) {
    return PsiTypeLookupItem.createLookupItem(TypeConversionUtil.erasure(GenericsUtil.getVariableTypeByExpressionType(type)), context).setShowPackage();
  }

  private static List<PsiClassType> extractClassTypes(ExpectedTypeInfo[] infos) {
    final List<PsiClassType> expectedClassTypes = new SmartList<>();
    for (PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)type;
        if (classType.resolve() != null) {
          expectedClassTypes.add(classType);
        }
      }
    }
    return expectedClassTypes;
  }

  @Nullable
  private LookupElement addExpectedType(final PsiType type,
                                        final CompletionParameters parameters) {
    if (!JavaCompletionUtil.hasAccessibleConstructor(type)) return null;

    final PsiClass psiClass = PsiUtil.resolveClassInType(type);
    if (psiClass == null || psiClass.getName() == null) return null;

    PsiElement position = parameters.getPosition();
    if ((parameters.getInvocationCount() < 2 || psiClass instanceof PsiCompiledElement) &&
        HighlightClassUtil.checkCreateInnerClassFromStaticContext(position, null, psiClass) != null &&
        !psiElement().afterLeaf(psiElement().withText(PsiKeyword.NEW).afterLeaf(".")).accepts(position)) {
      return null;
    }

    PsiType psiType = GenericsUtil.eliminateWildcards(type);
    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(parameters.getOriginalPosition()) &&
        PsiUtil.getLanguageLevel(parameters.getOriginalFile()).isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      if (psiClass.hasTypeParameters() && !((PsiClassType)type).isRaw()) {
        final String erasedText = TypeConversionUtil.erasure(psiType).getCanonicalText();
        String canonicalText = psiType.getCanonicalText();
        if (canonicalText.contains("?extends") || canonicalText.contains("?super")) {
          LOG.error("Malformed canonical text: " + canonicalText + "; presentable text: " + psiType + " of " + psiType.getClass() + "; " +
                    (psiType instanceof PsiClassReferenceType ? ((PsiClassReferenceType)psiType).getReference().getClass() : ""));
          return null;
        }
        try {
          boolean hasDefaultConstructor = PsiDiamondTypeImpl.hasDefaultConstructor(psiClass);
          boolean hasConstructorWithGenericsParameters = PsiDiamondTypeImpl.haveConstructorsGenericsParameters(psiClass);
          if (hasDefaultConstructor || !hasConstructorWithGenericsParameters) {
            String args;
            if (hasDefaultConstructor) {
              args = "";
            }
            else {
              //just try to resolve to the first constructor
              PsiParameter[] constructorParams = psiClass.getConstructors()[0].getParameterList().getParameters();
              args = StringUtil.join(constructorParams, p -> PsiTypesUtil.getDefaultValueOfType(p.getType()), ",");
            }
            final PsiStatement statement = elementFactory
              .createStatementFromText(canonicalText + " v = new " + erasedText + "<>(" + args + ")", parameters.getPosition());
            final PsiVariable declaredVar = (PsiVariable)((PsiDeclarationStatement)statement).getDeclaredElements()[0];
            final PsiNewExpression initializer = (PsiNewExpression)declaredVar.getInitializer();
            final PsiDiamondTypeImpl.DiamondInferenceResult inferenceResult = PsiDiamondTypeImpl.resolveInferredTypes(initializer);
            if (inferenceResult.getErrorMessage() == null &&
                !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
                areInferredTypesApplicable(inferenceResult.getTypes(), parameters.getPosition())) {
              psiType = initializer.getType();
            }
          }
        }
        catch (IncorrectOperationException ignore) {}
      }
    }
    final PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(psiType, position).setShowPackage();

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setIndicateAnonymous(true);
    }

    return LookupElementDecorator.withInsertHandler(item, myConstructorInsertHandler);
  }

  private static boolean areInferredTypesApplicable(@NotNull PsiType[] types, PsiElement position) {
    final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(position, PsiNewExpression.class, false);
    if (!PsiUtil.isLanguageLevel8OrHigher(position)) {
      return newExpression != null && PsiTypesUtil.getExpectedTypeByParent(newExpression) != null;
    }
    final PsiCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(newExpression, PsiCallExpression.class, false, PsiStatement.class);
    if (methodCallExpression != null && PsiUtil.isLanguageLevel8OrHigher(methodCallExpression)) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(newExpression.getParent());
      PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final int idx = argumentList != null ? ArrayUtil.find(argumentList.getExpressions(), parent) : -1;
      if (idx > -1) {
        final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
        final PsiMethod method = (PsiMethod)resolveResult.getElement();
        if (method != null) {
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          if (idx < parameters.length) {
            final PsiType expectedType = resolveResult.getSubstitutor().substitute(parameters[idx].getType());
            final PsiClass aClass = PsiUtil.resolveClassInType(expectedType);
            if (aClass != null) {
              final PsiClassType inferredArg = JavaPsiFacade.getElementFactory(method.getProject()).createType(aClass, types);
              return TypeConversionUtil.isAssignable(expectedType, inferredArg);
            }
          }
        }
      }
    }
    return true;
  }

  public static void processInheritors(final CompletionParameters parameters,
                                       Collection<PsiClassType> expectedClassTypes,
                                       final PrefixMatcher matcher, final Consumer<PsiType> consumer) {
    final PsiElement context = parameters.getPosition();
    GlobalSearchScope scope = context.getResolveScope();
    expectedClassTypes = ContainerUtil.mapNotNull(expectedClassTypes, type -> PsiClassImplUtil.correctType(type, scope));

    //quick
    if (!processMostProbableInheritors(parameters.getOriginalFile(), context, expectedClassTypes, consumer)) return;

    //long
    for (final PsiClassType type : expectedClassTypes) {
      CodeInsightUtil.processSubTypes(type, context, false, matcher, consumer);
    }
  }

  private static boolean processMostProbableInheritors(PsiFile contextFile,
                                                       PsiElement context,
                                                       Collection<PsiClassType> expectedClassTypes,
                                                       Consumer<PsiType> consumer) {
    for (final PsiClassType type : expectedClassTypes) {
      consumer.consume(type);

      final PsiClassType.ClassResolveResult baseResult = JavaCompletionUtil.originalize(type).resolveGenerics();
      final PsiClass baseClass = baseResult.getElement();
      if (baseClass == null) return false;

      final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();

      final Processor<PsiClass> processor = CodeInsightUtil.createInheritorsProcessor(context, type, 0, false,
                                                                                      consumer, baseClass, baseSubstitutor);
      final StatisticsInfo[] stats = StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getAfterNewKey(type));
      for (final StatisticsInfo statisticsInfo : stats) {
        final String value = statisticsInfo.getValue();
        if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
          final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
          final PsiClass psiClass = JavaPsiFacade.getInstance(contextFile.getProject()).findClass(qname, contextFile.getResolveScope());
          if (psiClass != null && !PsiTreeUtil.isAncestor(contextFile, psiClass, true) && !processor.process(psiClass)) break;
        }
      }
    }
    return true;
  }
}
