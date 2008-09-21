/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.patterns.PsiJavaElementPattern;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.classes.ThisOrAnyInnerFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.LangBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class JavaClassNameCompletionContributor extends CompletionContributor {
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_THROW_NEW = psiElement().afterLeaf(
      psiElement().withText(PsiKeyword.NEW).afterLeaf(PsiKeyword.THROW));
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(PsiKeyword.NEW);
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
      psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.SUPER, "&").withParent(
          psiElement(PsiReferenceList.class).withParent(PsiTypeParameter.class));
  private static final PsiJavaElementPattern.Capture<PsiElement> INSIDE_METHOD_THROWS_CLAUSE = psiElement().afterLeaf(PsiKeyword.THROWS, ",").inside(
      PsiMethod.class).andNot(psiElement().inside(PsiCodeBlock.class)).andNot(psiElement().inside(PsiParameterList.class));

  public JavaClassNameCompletionContributor() {
    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>(true, false) {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        PsiElement insertedElement = parameters.getPosition();
        String prefix = result.getPrefixMatcher().getPrefix();

        final PsiFile file = parameters.getOriginalFile();
        final Project project = file.getProject();

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

        final StatisticsInfo[] infos =
            StatisticsManager.getInstance().getAllValues(JavaCompletionStatistician.CLASS_NAME_COMPLETION_PREFIX + StringUtil.capitalsOnly(prefix));
        for (final StatisticsInfo info : infos) {
          final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
            public PsiClass[] compute() {
              return JavaPsiFacade.getInstance(project).findClasses(info.getValue(), file.getResolveScope());
            }
          });
          for (final PsiClass psiClass : classes) {
            result.addElement(AllClassesGetter.createLookupItem(psiClass));
          }
        }

        if (afterNew) {
          final PsiExpression expr = PsiTreeUtil.getContextOfType(insertedElement, PsiExpression.class, true);
          if (expr != null) {
            final ExpectedTypeInfo[] expectedInfos =
                ApplicationManager.getApplication().runReadAction(new Computable<ExpectedTypeInfo[]>() {
                  public ExpectedTypeInfo[] compute() {
                    return ExpectedTypesProvider.getInstance(project).getExpectedTypes(expr, true);
                  }
                });
            for (final ExpectedTypeInfo info : expectedInfos) {
              final PsiType type = info.getType();
              final PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
                public PsiClass compute() {
                  return PsiUtil.resolveClassInType(type);
                }
              });
              if (psiClass != null) {
                result.addElement(AllClassesGetter.createLookupItem(psiClass));
              }
              final PsiType defaultType = info.getDefaultType();
              if (!defaultType.equals(type)) {
                final PsiClass defClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
                  public PsiClass compute() {
                    return PsiUtil.resolveClassInType(defaultType);
                  }
                });
                if (defClass != null) {
                  result.addElement(AllClassesGetter.createLookupItem(defClass));
                }
              }
            }
          }
        }

        getter.getClasses(insertedElement, result, parameters.getOffset(), parameters.getInvocationCount() == 1);
      }
    });

  }

  public String advertise(@NotNull final CompletionParameters parameters) {
    if (shouldShowSecondSmartCompletionHint(parameters) &&
        CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION)) {
      return CompletionBundle.message("completion.class.name.hint.2", getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION));
    }

    return null;
  }

  @Override
  public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
    if (!(parameters.getOriginalFile() instanceof PsiJavaFile)) return null;

    if (shouldShowSecondSmartCompletionHint(parameters)) {
      return LangBundle.message("completion.no.suggestions") +
             "; " +
             StringUtil.decapitalize(
                 CompletionBundle.message("completion.class.name.hint.2", getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION)));
    }

    return null;
  }

  private static boolean shouldShowSecondSmartCompletionHint(final CompletionParameters parameters) {
    return parameters.getCompletionType() == CompletionType.CLASS_NAME &&
           parameters.getInvocationCount() == 1 &&
           parameters.getOriginalFile().getLanguage() == StdLanguages.JAVA;
  }
}
