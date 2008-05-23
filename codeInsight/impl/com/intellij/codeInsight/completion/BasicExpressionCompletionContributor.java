/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.not;
import static com.intellij.patterns.StandardPatterns.or;
import com.intellij.psi.*;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.filters.GeneratorFilter;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.getters.*;
import com.intellij.psi.filters.types.AssignableGroupFilter;
import com.intellij.psi.filters.types.ReturnTypeFilter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class BasicExpressionCompletionContributor extends ExpressionSmartCompletionContributor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.BasicExpressionCompletionContributor");

  private static void addKeyword(final CompletionResultSet result, final PsiElement element, final GeneratorFilter filter, final String s) {
    try {
      final PsiKeyword keyword = JavaPsiFacade.getInstance(element.getProject()).getElementFactory().createKeyword(s);
      if (filter.isAcceptable(keyword, element)) {
        result.addElement(
            LookupItemUtil.objectToLookupItem(keyword).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE));
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }


  public BasicExpressionCompletionContributor() {
    final GeneratorFilter filter = new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter());

    extend(PsiJavaPatterns.psiElement().afterLeaf(
        PsiJavaPatterns.psiElement().withText(".").afterLeaf(
            PsiJavaPatterns.psiElement().withParent(
                PsiJavaPatterns.psiElement().referencing(psiClass())))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();
        addKeyword(result, element, filter, PsiKeyword.CLASS);
        addKeyword(result, element, filter, PsiKeyword.THIS);
      }

    });

    extend(PsiJavaPatterns.psiElement().withSuperParent(2,
                                                        or(
                                                            PsiJavaPatterns.psiElement(PsiConditionalExpression.class).withParent(
                                                                PsiReturnStatement.class),
                                                            PsiJavaPatterns.psiElement(PsiReturnStatement.class))), new CompletionProvider<JavaSmartCompletionParameters>() {
      public void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final PsiElement element = parameters.getPosition();

        final PsiElement parent = element.getParent();
        if (parent instanceof PsiReferenceExpression && ((PsiReferenceExpression)parent).getQualifierExpression() != null) return;

        for (final PsiType type : ExpectedTypesGetter.getExpectedTypes(element)) {
          if (type instanceof PsiClassType) {
            final PsiClass expectedClass = ((PsiClassType)type).resolve();
            if (expectedClass != null) {
              final PsiClass collectionClass = JavaPsiFacade.getInstance(expectedClass.getProject())
                  .findClass(CommonClassNames.JAVA_UTIL_COLLECTION, element.getResolveScope());
              final boolean isCollection =
                  collectionClass != null && expectedClass.getManager().areElementsEquivalent(expectedClass, collectionClass);

              addCollectionMethod(element, result, expectedClass, isCollection, CommonClassNames.JAVA_UTIL_LIST, "emptyList");
              addCollectionMethod(element, result, expectedClass, isCollection, CommonClassNames.JAVA_UTIL_SET, "emptySet");
              addCollectionMethod(element, result, expectedClass, isCollection, CommonClassNames.JAVA_UTIL_MAP, "emptyMap");
            }
          }
        }
      }

      private void addCollectionMethod(final PsiElement element, final CompletionResultSet result, final PsiClass expectedClass,
                                              final boolean collection,
                                              final String baseClassName,
                                              @NonNls final String method) {
        final PsiManager manager = expectedClass.getManager();
        final GlobalSearchScope scope = element.getResolveScope();
        if (collection || manager.areElementsEquivalent(expectedClass,
                                                        JavaPsiFacade.getInstance(manager.getProject()).findClass(baseClassName, scope))) {
          final PsiClass collectionsClass =
            JavaPsiFacade.getInstance(manager.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTIONS, scope);
          if (collectionsClass != null) {
            final PsiMethod[] methods = collectionsClass.findMethodsByName(method, false);
            if (methods.length != 0) {
              result.addElement(JavaAwareCompletionData.qualify(
              LookupItemUtil.objectToLookupItem(methods[0]).setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE).setTailType(
                  TailType.NONE)));
            }
          }
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

    extend(not(psiElement().afterLeaf(".")), new CompletionProvider<JavaSmartCompletionParameters>() {
      protected void addCompletions(@NotNull final JavaSmartCompletionParameters parameters, final ProcessingContext context, @NotNull final CompletionResultSet result) {
        final ClassLiteralGetter classGetter =
            new ClassLiteralGetter(new FilterGetter(new ExpectedTypesGetter(), new ExcludeDeclaredFilter(new ClassFilter(PsiClass.class))));
        for (final LookupElement<PsiExpression> element : classGetter.get(parameters.getPosition(), null)) {
          result.addElement(element.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
        }

        final FilterGetter templateGetter = new FilterGetter(new TemplatesGetter(), new ReturnTypeFilter(new GeneratorFilter(AssignableGroupFilter.class, new ExpectedTypesGetter())));
        for (final Object o : templateGetter.get(parameters.getPosition(), null)) {
          result.addElement(LookupItemUtil.objectToLookupItem(o));
        }

        final MembersGetter membersGetter = new MembersGetter(new ExpectedTypesGetter());
        for (final PsiElement element : membersGetter.get(parameters.getPosition(), null)) {
          final LookupItem item = LookupItemUtil.objectToLookupItem(element);
          JavaAwareCompletionData.qualify(item);
          result.addElement(item);
        }

        final PsiElement position = parameters.getPosition();
        addKeyword(result, position, filter, PsiKeyword.TRUE);
        addKeyword(result, position, filter, PsiKeyword.FALSE);

        for (final PsiExpression expression : ThisGetter.getThisExpressionVariants(position)) {
          if (filter.isAcceptable(expression.getType(), position)) {
            result.addElement(LookupItemUtil.objectToLookupItem(expression));
          }
        }
      }
    });

    extend(psiElement().afterLeaf(PsiKeyword.CASE), new EnumConstantsGetter());

  }
}
