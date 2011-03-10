/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.LangBundle;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author peter
 */
public class JavaClassNameCompletionContributor extends CompletionContributor {
  private static final PsiJavaElementPattern.Capture<PsiElement> AFTER_NEW = psiElement().afterLeaf(PsiKeyword.NEW);
  private static final PsiJavaElementPattern.Capture<PsiElement> IN_TYPE_PARAMETER =
      psiElement().afterLeaf(PsiKeyword.EXTENDS, PsiKeyword.SUPER, "&").withParent(
          psiElement(PsiReferenceList.class).withParent(PsiTypeParameter.class));

  public JavaClassNameCompletionContributor() {
    extend(CompletionType.CLASS_NAME, psiElement(), new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet _result) {
        if (shouldShowSecondSmartCompletionHint(parameters) &&
            CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.SECOND_CLASS_NAME_COMPLETION)) {
          CompletionService.getCompletionService().setAdvertisementText(CompletionBundle.message("completion.class.name.hint.2", getActionShortcut(IdeActions.ACTION_CLASS_NAME_COMPLETION)));
        }

        final CompletionResultSet result = JavaCompletionSorting.addJavaSorting(parameters, _result);

        addAllClasses(parameters, result, new Consumer<LookupElement>() {
          @Override
          public void consume(LookupElement element) {
            _result.addElement(element);
          }
        });
      }
    });
  }

  public static void addAllClasses(CompletionParameters parameters, final CompletionResultSet result, @NotNull final Consumer<LookupElement> consumer) {
    final PsiElement insertedElement = parameters.getPosition();

    final ElementFilter filter =
      or(JavaSmartCompletionContributor.AFTER_THROW_NEW,
         JavaCompletionContributor.INSIDE_METHOD_THROWS_CLAUSE,
         JavaCompletionContributor.IN_CATCH_TYPE,
         JavaCompletionContributor.IN_MULTI_CATCH_TYPE).accepts(insertedElement)
      ? new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE)
      : IN_TYPE_PARAMETER.accepts(insertedElement)
        ? new ExcludeDeclaredFilter(new ClassFilter(PsiTypeParameter.class))
        : TrueFilter.INSTANCE;

    final boolean inJavaContext = parameters.getPosition() instanceof PsiIdentifier;
    if (AFTER_NEW.accepts(insertedElement)) {
      final PsiExpression expr = PsiTreeUtil.getContextOfType(insertedElement, PsiExpression.class, true);
      for (final ExpectedTypeInfo info : ExpectedTypesProvider.getExpectedTypes(expr, true)) {
        final PsiType type = info.getType();
        final PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null) {
          consumer.consume(createClassLookupItem(psiClass, inJavaContext));
        }
        final PsiType defaultType = info.getDefaultType();
        if (!defaultType.equals(type)) {
          final PsiClass defClass = PsiUtil.resolveClassInType(defaultType);
          if (defClass != null) {
            consumer.consume(createClassLookupItem(defClass, inJavaContext));
          }
        }
      }
    }

    final boolean lookingForAnnotations = PsiJavaPatterns.psiElement().afterLeaf("@").accepts(insertedElement);

    AllClassesGetter
      .processJavaClasses(parameters, result.getPrefixMatcher(), parameters.getInvocationCount() <= 1, new Consumer<PsiClass>() {
        @Override
        public void consume(PsiClass psiClass) {
          if (lookingForAnnotations && !psiClass.isAnnotationType()) return;

          if (filter.isAcceptable(psiClass, insertedElement)) {
            consumer.consume(createClassLookupItem(psiClass, inJavaContext));
          }
        }
      });
  }

  public static JavaPsiClassReferenceElement createClassLookupItem(final PsiClass psiClass, final boolean inJavaContext) {
    return AllClassesGetter.createLookupItem(psiClass, inJavaContext ? JavaClassNameInsertHandler.JAVA_CLASS_INSERT_HANDLER : AllClassesGetter.TRY_SHORTENING);
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
