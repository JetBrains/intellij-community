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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

/**
 * @author peter
 */
public class JavaInheritorsGetter extends CompletionProvider<CompletionParameters> {
  private final ConstructorInsertHandler myConstructorInsertHandler;

  public JavaInheritorsGetter(final ConstructorInsertHandler constructorInsertHandler) {
    myConstructorInsertHandler = constructorInsertHandler;
  }

  public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final ExpectedTypeInfo[] infos = JavaSmartCompletionContributor.getExpectedTypes(parameters);

    final List<ExpectedTypeInfo> infoCollection = Arrays.asList(infos);
    generateVariants(parameters, result.getPrefixMatcher(), infos, new Consumer<LookupElement>() {
      @Override
      public void consume(LookupElement lookupElement) {
        result.addElement(JavaSmartCompletionContributor.decorate(lookupElement, infoCollection));
      }
    });
  }

  public void generateVariants(final CompletionParameters parameters, final PrefixMatcher prefixMatcher, final Consumer<LookupElement> consumer) {
    generateVariants(parameters, prefixMatcher, JavaSmartCompletionContributor.getExpectedTypes(parameters), consumer);
  }

  private void generateVariants(final CompletionParameters parameters, final PrefixMatcher prefixMatcher,
                                final ExpectedTypeInfo[] infos, final Consumer<LookupElement> consumer) {

    addArrayTypes(parameters.getPosition(), infos, consumer);

    processInheritors(parameters, extractClassTypes(infos), prefixMatcher, new Consumer<PsiType>() {
      public void consume(final PsiType type) {
        final LookupElement element = addExpectedType(type, parameters);
        if (element != null) {
          consumer.consume(element);
        }
      }
    });
  }

  private static void addArrayTypes(PsiElement identifierCopy,
                                    ExpectedTypeInfo[] infos, final Consumer<LookupElement> consumer) {

    for (final PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiArrayType) {
        final LookupItem item = PsiTypeLookupItem.createLookupItem(TypeConversionUtil.erasure(type), identifierCopy);
        if (item.getObject() instanceof PsiClass) {
          JavaCompletionUtil.setShowFQN(item);
        }
        item.setInsertHandler(new DefaultInsertHandler()); //braces & shortening
        consumer.consume(item);
      }
    }
  }

  private static List<PsiClassType> extractClassTypes(ExpectedTypeInfo[] infos) {
    final List<PsiClassType> expectedClassTypes = new SmartList<PsiClassType>();
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

    PsiType psiType = JavaCompletionUtil.eliminateWildcards(type);
    if (JavaSmartCompletionContributor.AFTER_NEW.accepts(parameters.getOriginalPosition()) &&
        PsiUtil.getLanguageLevel(parameters.getOriginalFile()).isAtLeast(LanguageLevel.JDK_1_7)) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
      if (psiClass.hasTypeParameters() && !((PsiClassType)type).isRaw()) {
        final String canonicalText = TypeConversionUtil.erasure(psiType).getCanonicalText();
        final PsiStatement statement = elementFactory
          .createStatementFromText(psiType.getCanonicalText() + " v = new " + canonicalText + "<>()", parameters.getOriginalFile());
        final PsiVariable declaredVar = (PsiVariable)((PsiDeclarationStatement)statement).getDeclaredElements()[0];
        final PsiNewExpression initializer = (PsiNewExpression)declaredVar.getInitializer();
        final boolean hasDefaultConstructorOrNoGenericsOne = PsiDiamondTypeImpl.hasDefaultConstructor(psiClass) ||
                                                             !PsiDiamondTypeImpl.haveConstructorsGenericsParameters(psiClass);
        if (hasDefaultConstructorOrNoGenericsOne) {
          final PsiDiamondTypeImpl.DiamondInferenceResult inferenceResult = PsiDiamondTypeImpl.resolveInferredTypes(initializer);
          if (inferenceResult.getErrorMessage() == null &&
              !psiClass.hasModifierProperty(PsiModifier.ABSTRACT) &&
              areInferredTypesApplicable(inferenceResult.getTypes(), parameters.getOriginalPosition())) {
            psiType = initializer.getType();
          }
        }
      }
    }
    final PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(psiType, position);
    JavaCompletionUtil.setShowFQN(item);

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setIndicateAnonymous(true);
    }

    return LookupElementDecorator.withInsertHandler(item, myConstructorInsertHandler);
  }

  private static boolean areInferredTypesApplicable(@NotNull PsiType[] types, PsiElement originalPosition) {
    final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(originalPosition, PsiMethodCallExpression.class);
    if (methodCallExpression != null) {
      final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(originalPosition, PsiNewExpression.class);
      if (newExpression != null && ArrayUtil.find(methodCallExpression.getArgumentList().getExpressions(), newExpression) > -1 ||
          Comparing.equal(originalPosition.getParent(), methodCallExpression.getArgumentList())) {
        final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
        final PsiMethod method = (PsiMethod)resolveResult.getElement();
        return method == null ||
               PsiUtil.getApplicabilityLevel(method, resolveResult.getSubstitutor(), types, PsiUtil.getLanguageLevel(originalPosition))
               != MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;
      }
    }
    return true;
  }

  public static void processInheritors(final CompletionParameters parameters,
                                       final Collection<PsiClassType> expectedClassTypes,
                                       final PrefixMatcher matcher, final Consumer<PsiType> consumer) {
    //quick
    if (!processMostProbableInheritors(parameters, expectedClassTypes, consumer)) return;

    //long
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };
    for (final PsiClassType type : expectedClassTypes) {
      final PsiClass psiClass = type.resolve();
      if (psiClass != null && !psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        CodeInsightUtil.processSubTypes(type, parameters.getPosition(), false, shortNameCondition, consumer);
      }
    }
  }

  private static boolean processMostProbableInheritors(CompletionParameters parameters,
                                                       Collection<PsiClassType> expectedClassTypes,
                                                       Consumer<PsiType> consumer) {
    PsiFile file = parameters.getOriginalFile();
    for (final PsiClassType type : expectedClassTypes) {
      consumer.consume(type);

      final PsiClassType.ClassResolveResult baseResult = JavaCompletionUtil.originalize(type).resolveGenerics();
      final PsiClass baseClass = baseResult.getElement();
      if (baseClass == null) return false;

      final PsiSubstitutor baseSubstitutor = baseResult.getSubstitutor();

      final Processor<PsiClass> processor = CodeInsightUtil.createInheritorsProcessor(parameters.getPosition(), type, 0, false,
                                                                                      consumer, baseClass, baseSubstitutor);
      final StatisticsInfo[] stats = StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getAfterNewKey(type));
      for (final StatisticsInfo statisticsInfo : stats) {
        final String value = statisticsInfo.getValue();
        if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
          final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
          final PsiClass psiClass = JavaPsiFacade.getInstance(file.getProject()).findClass(qname, file.getResolveScope());
          if (psiClass != null && !PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
        }
      }
    }
    return true;
  }
}
