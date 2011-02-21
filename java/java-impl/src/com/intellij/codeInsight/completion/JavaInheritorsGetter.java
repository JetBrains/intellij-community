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
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.statistics.JavaStatisticsManager;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

    addArrayTypes(parameters.getPosition(), infos, prefixMatcher, consumer);

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
                                    ExpectedTypeInfo[] infos, PrefixMatcher matcher, final Consumer<LookupElement> consumer) {

    for (final PsiType type : ExpectedTypesGetter.extractTypes(infos, true)) {
      if (type instanceof PsiArrayType && matcher.prefixMatches(type.getCanonicalText())) {

        final LookupItem item = PsiTypeLookupItem.createLookupItem(JavaCompletionUtil.eliminateWildcards(type), identifierCopy);
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
    if (psiClass == null) return null;

    final PsiClass parentClass = psiClass.getContainingClass();
    if (parentClass != null && !psiClass.hasModifierProperty(PsiModifier.STATIC) &&
        !PsiTreeUtil.isAncestor(parentClass, parameters.getPosition(), false) &&
        !(parentClass.getContainingFile().equals(parameters.getOriginalFile()) &&
          parentClass.getTextRange().contains(parameters.getOffset()))) {
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
        final boolean hasDefaultConstructorOrNoGenericsOne = PsiDiamondType.hasDefaultConstructor(psiClass) || !PsiDiamondType.haveConstructorsGenericsParameters(psiClass);
        if (hasDefaultConstructorOrNoGenericsOne && PsiDiamondType.resolveInferredTypes(initializer).getErrorMessage() == null) {
          psiType = initializer.getType();
        }
      }
    }
    final LookupItem item = PsiTypeLookupItem.createLookupItem(psiType, parameters.getPosition());
    JavaCompletionUtil.setShowFQN(item);

    if (psiClass.isInterface() || psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
      item.setAttribute(LookupItem.INDICATE_ANONYMOUS, "");
    }

    return LookupElementDecorator.withInsertHandler(item, myConstructorInsertHandler);
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
        for (final PsiType psiType : CodeInsightUtil.addSubtypes(type, parameters.getPosition(), false, shortNameCondition)) {
          consumer.consume(psiType);
        }
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

      final THashSet<PsiType> statVariants = new THashSet<PsiType>();
      final Processor<PsiClass> processor = CodeInsightUtil.createInheritorsProcessor(parameters.getPosition(), type, 0, false,
                                                                                      statVariants, baseClass, baseSubstitutor);
      final StatisticsInfo[] stats = StatisticsManager.getInstance().getAllValues(JavaStatisticsManager.getAfterNewKey(type));
      for (final StatisticsInfo statisticsInfo : stats) {
        final String value = statisticsInfo.getValue();
        if (value.startsWith(JavaStatisticsManager.CLASS_PREFIX)) {
          final String qname = value.substring(JavaStatisticsManager.CLASS_PREFIX.length());
          final PsiClass psiClass = JavaPsiFacade.getInstance(file.getProject()).findClass(qname, file.getResolveScope());
          if (psiClass != null && !PsiTreeUtil.isAncestor(file, psiClass, true) && !processor.process(psiClass)) break;
        }
      }

      for (final PsiType variant : statVariants) {
        consumer.consume(variant);
      }
    }
    return true;
  }
}
