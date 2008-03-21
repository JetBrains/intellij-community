/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.classes.ThisOrAnyInnerFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.codeInsight.completion.AllClassesGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaClassNameCompletionContributor extends CompletionContributor{
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_THROW_NEW = psiElement().afterLeaf(
      psiElement().withText(PsiKeyword.NEW).afterLeaf(PsiKeyword.THROW));
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(PsiKeyword.NEW);
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
      psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.SUPER, "&").withParent(
          psiElement(PsiReferenceList.class).withParent(PsiTypeParameter.class));
  private static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_METHOD_THROWS_CLAUSE = psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(
      PsiMethod.class).andNot(psiElement().inside(PsiCodeBlock.class)).andNot(psiElement().inside(PsiParameterList.class));

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(CompletionType.CLASS_NAME, psiElement()).withId(JavaCompletionContributor.JAVA_LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        PsiElement insertedElement = parameters.getPosition();
        result.setPrefixMatcher(CompletionData.findPrefixStatic(insertedElement, context.getStartOffset()));

        AllClassesGetter getter = new AllClassesGetter(TrueFilter.INSTANCE);
        boolean afterNew = AFTER_NEW.accepts(insertedElement);
        if (AFTER_THROW_NEW.accepts(insertedElement)) {
          getter = new AllClassesGetter(new AssignableFromFilter("java.lang.Throwable"));
        }
        else if (IN_TYPE_PARAMETER.accepts(insertedElement)) {
          getter = new AllClassesGetter(new ExcludeDeclaredFilter(new ClassFilter(PsiTypeParameter.class)));
        }
        else if (INSIDE_METHOD_THROWS_CLAUSE.accepts(insertedElement)) {
          getter = new AllClassesGetter(new ThisOrAnyInnerFilter(new AssignableFromFilter("java.lang.Throwable")));
        }

        getter.getClasses(insertedElement, context, result, afterNew);
      }
    });

    registrar.extend(CompletionType.CLASS_NAME, psiElement()).withId("green").dependingOn(JavaCompletionContributor.JAVA_LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        PsiElement insertedElement = parameters.getPosition();
        final String prefix = CompletionData.findPrefixStatic(insertedElement, context.getStartOffset());
        result.setPrefixMatcher(prefix);

        final PsiFile file = parameters.getOriginalFile();
        final Project project = file.getProject();

        boolean afterNew = AFTER_NEW.accepts(insertedElement);

        final StatisticsInfo[] infos =
            StatisticsManager.getInstance().getAllValues(JavaCompletionStatistician.CLASS_NAME_COMPLETION_PREFIX + StringUtil.capitalsOnly(prefix));
        for (final StatisticsInfo info : infos) {
          final PsiClass[] classes = JavaPsiFacade.getInstance(project).findClasses(info.getValue(), file.getResolveScope());
          for (final PsiClass psiClass : classes) {
            result.addElement(AllClassesGetter.createLookupItem(psiClass, afterNew));
          }
        }

        if (afterNew) {
          final PsiExpression expr = PsiTreeUtil.getContextOfType(parameters.getPosition(), PsiExpression.class, true);
          if (expr != null) {
            final ExpectedTypeInfo[] expectedInfos = ExpectedTypesProvider.getInstance(project).getExpectedTypes(expr, true);
            for (final ExpectedTypeInfo info : expectedInfos) {
              final PsiType type = info.getType();
              final PsiClass psiClass = PsiUtil.resolveClassInType(type);
              if (psiClass != null) {
                result.addElement(AllClassesGetter.createLookupItem(psiClass, afterNew));
              }
              final PsiType defaultType = info.getDefaultType();
              if (!defaultType.equals(type)) {
                final PsiClass defClass = PsiUtil.resolveClassInType(defaultType);
                if (defClass != null) {
                  result.addElement(AllClassesGetter.createLookupItem(defClass, afterNew));
                }
              }
            }
          }
        }
      }
    });

  }

}