/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.psi.filters.getters.CastTypeGetter;
import com.intellij.psi.filters.getters.ExpectedTypesGetter;
import com.intellij.psi.filters.getters.ThrowsListGetter;
import com.intellij.psi.filters.types.AssignableFromFilter;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.AssignableToFilter;
import com.intellij.psi.filters.types.ReturnTypeFilter;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import gnu.trove.THashSet;

/**
 * @author peter
 */
public class AnywhereSmartCompletionContributor extends JavaSmartCompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.AnywhereSmartCompletionContributor");
  private static final OrFilter THROWABLE_TYPE_FILTER = new OrFilter(
      new GeneratorFilter(AssignableGroupFilter.class, new ThrowsListGetter()),
      new AssignableFromFilter("java.lang.RuntimeException"));

  @Nullable
  private static Pair<ElementFilter, TailType> getReferenceFilter(PsiElement element) {
    final ReturnTypeFilter valueTypeFilter =
      new ReturnTypeFilter(new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter()));

    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ReturnTypeFilter(THROWABLE_TYPE_FILTER), TailType.SEMICOLON);
    }

    //throw new foo
    if (JavaSmartCompletionData.AFTER_THROW_NEW.accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(THROWABLE_TYPE_FILTER), TailType.SEMICOLON);
    }

    if (psiElement().inside(
        psiElement(PsiReferenceList.class).save("refList").withParent(
            PsiJavaPatterns.psiMethod().withThrowsList(get("refList")))).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new AssignableFromFilter(CommonClassNames.JAVA_LANG_THROWABLE)), TailType.NONE);
    }

    if (JavaSmartCompletionData.AFTER_NEW.accepts(element)) {
      return null;
    }


    if (psiElement().afterLeaf(PsiKeyword.INSTANCEOF).accepts(element)) {
      return null;
    }

    if (psiElement().afterLeaf(psiElement().withText(")").withParent(PsiTypeCastExpression.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ReturnTypeFilter(new GeneratorFilter(AssignableToFilter.class, new CastTypeGetter())), TailType.NONE);
    }
    if (psiElement().afterLeaf(psiElement().withText("(").withParent(PsiTypeCastExpression.class)).accepts(element)) {
      return null;
    }

    if (psiElement().afterLeaf(psiElement().withText(".")).withSuperParent(2, psiElement(PsiNewExpression.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter()), TailType.UNKNOWN);
    }

    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new AndFilter(valueTypeFilter,
        new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiMethod.class)))
      ), TailType.UNKNOWN);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiAnnotationParameterList.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL),
          new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter())
        )), TailType.NONE);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiJavaPatterns.psiElement(PsiVariable.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new AndFilter(valueTypeFilter,
                                                             new ElementExtractorFilter(
                                                                 new ExcludeDeclaredFilter(
                                                                     new ClassFilter(
                                                                         PsiVariable.class))),
                                                             new ElementExtractorFilter(
                                                                 new ExcludeSillyAssignment())), TailType.NONE);
    }

    return new Pair<ElementFilter, TailType>(
        new AndFilter(valueTypeFilter, new ElementExtractorFilter(new ExcludeSillyAssignment())), TailType.NONE);
  }

  public AnywhereSmartCompletionContributor() {
    final GeneratorFilter filter = new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter());

    extend(psiElement(), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final PsiReference reference = element.getContainingFile().findReferenceAt(parameters.getOffset());
        if (reference != null) {
          final Pair<ElementFilter, TailType> pair = getReferenceFilter(element);
          if (pair != null) {
            final THashSet<LookupItem> set = new THashSet<LookupItem>();
            LegacyJavaSmartCompletionContributor.SMART_DATA.completeReference(reference, element, set, pair.second, result.getPrefixMatcher(), parameters.getOriginalFile(), pair.first, new CompletionVariant());
            for (final LookupItem item : set) {
              if (JavaSmartCompletionData.AFTER_THROW_NEW.accepts(element)) {
                item.setAttribute(LookupItem.DONT_CHECK_FOR_INNERS, "");
                if (item.getObject() instanceof PsiClass) {
                  JavaAwareCompletionData.setShowFQN(item);
                }
              }
              result.addElement(item);
            }
          }

        }
      }
    });

    extend(PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        try {
          final PsiElement element = parameters.getPosition();
          addKeyword(result, element, filter, PsiKeyword.CLASS);
          addKeyword(result, element, filter, PsiKeyword.THIS);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

      private void addKeyword(final CompletionResultSet result, final PsiElement element, final GeneratorFilter filter, final String s)
          throws IncorrectOperationException {
        final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
        if (filter.isAcceptable(keyword, element)) {
          result.addElement(
              LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE));
        }
      }

    });

    extend(PsiJavaPatterns.psiElement().inside(
        PsiJavaPatterns.psiElement(PsiDocTag.class).withName(
            string().oneOf(PsiKeyword.THROWS, JavaSmartCompletionData.EXCEPTION_TAG))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : new ThrowsListGetter().get(element, completionContext)) {
          result.addElement(LookupItemUtil.objectToLookupItem(object).setTailType(TailType.SPACE));
        }
      }
    });

    extend(PsiJavaPatterns.psiElement().withSuperParent(2,
                                                        or(
                                                            PsiJavaPatterns.psiElement(PsiConditionalExpression.class).withParent(
                                                                PsiReturnStatement.class),
                                                            PsiJavaPatterns.psiElement(PsiReturnStatement.class))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ContextGetter chooser = new JavaSmartCompletionData.EmptyCollectionGetter();
        final PsiElement element = parameters.getPosition();
        final CompletionContext completionContext = element.getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        for (final Object object : chooser.get(element, completionContext)) {
          result.addElement(JavaAwareCompletionData.qualify(
              LookupItemUtil.objectToLookupItem(object).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE).setTailType(
                  TailType.NONE)));
        }
      }
    });

    extend(or(
        PsiJavaPatterns.psiElement().withParent(PsiNameValuePair.class),
        PsiJavaPatterns.psiElement().withSuperParent(2, PsiNameValuePair.class)), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        final ElementPattern<? extends PsiElement> leftNeighbor = PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("."));
        final boolean needQualify = leftNeighbor.accepts(element);

        for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element)) {
          final PsiClass psiClass = PsiUtil.resolveClassInType(type);
          if (psiClass != null && psiClass.isAnnotationType()) {
            final LookupItem item = LookupItemUtil.objectToLookupItem(type).setTailType(TailType.NONE);
            if (needQualify) JavaAwareCompletionData.qualify(item);
            result.addElement(item);
          }
        }

      }
    });
  }
}
