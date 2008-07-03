/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.filters.*;
import com.intellij.psi.filters.element.ExcludeDeclaredFilter;
import com.intellij.psi.filters.element.ExcludeSillyAssignment;
import com.intellij.psi.filters.element.ModifierFilter;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class ReferenceExpressionCompletionContributor extends ExpressionSmartCompletionContributor{

  @Nullable
  private static Pair<ElementFilter, TailType> getReferenceFilter(PsiElement element) {
    //throw foo
    if (psiElement().withParent(psiElement(PsiReferenceExpression.class).withParent(PsiThrowStatement.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(TrueFilter.INSTANCE, TailType.SEMICOLON);
    }

    if (psiElement().afterLeaf(psiElement().withText(")").withParent(PsiTypeCastExpression.class)).accepts(element)) {
      return null;
    }

    if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.RETURN).inside(PsiReturnStatement.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiMethod.class))
      ), TailType.UNKNOWN);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiAnnotationParameterList.class).accepts(element)) {
      return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new AndFilter(
          new ClassFilter(PsiField.class),
          new ModifierFilter(PsiKeyword.STATIC, PsiKeyword.FINAL)
      )), TailType.NONE);
    }

    if (PsiJavaPatterns.psiElement().inside(PsiJavaPatterns.psiElement(PsiVariable.class)).accepts(element)) {
      return new Pair<ElementFilter, TailType>(
          new AndFilter(new ElementExtractorFilter(new ExcludeDeclaredFilter(new ClassFilter(PsiVariable.class))),
                        new ElementExtractorFilter(new ExcludeSillyAssignment())), TailType.NONE);
    }

    return new Pair<ElementFilter, TailType>(new ElementExtractorFilter(new ExcludeSillyAssignment()), TailType.NONE);
  }

  public boolean fillCompletionVariants(final JavaSmartCompletionParameters parameters, final CompletionResultSet result) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final PsiElement element = parameters.getPosition();
        final int offset = parameters.getOffset();
        final PsiReference reference = element.getContainingFile().findReferenceAt(offset);
        if (reference != null) {
          final Pair<ElementFilter, TailType> pair = getReferenceFilter(element);
          if (pair != null) {
            final PsiFile originalFile = parameters.getOriginalFile();
            final TailType tailType = pair.second;
            final ElementFilter filter = pair.first;
            final THashSet<LookupItem> set = completeReference(element, reference, originalFile, tailType, filter, result);
            for (final LookupItem item : set) {
              result.addElement(item);
            }

            if (parameters.getInvocationCount() >= 2) {
              for (final LookupItem<?> qualifier : completeReference(element, reference, originalFile, tailType, TrueFilter.INSTANCE, result)) {
                final String prefix = getItemText(qualifier.getObject());
                if (prefix == null) continue;

                final PsiSubstitutor substitutor = (PsiSubstitutor)qualifier.getAttribute(LookupItem.SUBSTITUTOR);

                try {
                  final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(element.getProject()).getElementFactory();
                  final PsiExpression ref = elementFactory.createExpressionFromText(prefix + ".xxx", element);
                  for (final LookupItem<?> item : completeReference(element, (PsiReferenceExpression)ref, originalFile, tailType, filter, result)) {
                    if (item.getObject() instanceof PsiMethod) {
                      final PsiMethod method = (PsiMethod)item.getObject();
                      final QualifiedMethodLookupItem newItem = new QualifiedMethodLookupItem(method, prefix);
                      final PsiSubstitutor newSubstitutor = (PsiSubstitutor)item.getAttribute(LookupItem.SUBSTITUTOR);
                      if (substitutor != null || newSubstitutor != null) {
                        newItem.setAttribute(LookupItem.SUBSTITUTOR, substitutor == null ? newSubstitutor :
                                                                     newSubstitutor == null ? substitutor : substitutor.putAll(newSubstitutor));
                      }
                      result.addElement(newItem);
                    } else {
                      item.setAttribute(JavaCompletionUtil.QUALIFIER_PREFIX_ATTRIBUTE, prefix + ".");
                      item.setLookupString(prefix + "." + item.getLookupString());
                      result.addElement(item);
                    }
                  }
                }
                catch (IncorrectOperationException e) {
                }
              }
            }
          }
        }
      }
    });
    return true;
  }

  @Nullable
  private static String getItemText(Object o) {
    if (o instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)o;
      final PsiType type = method.getReturnType();
      if (PsiType.VOID.equals(type) || PsiType.NULL.equals(type)) return null;
      if (method.getParameterList().getParametersCount() > 0) return null;
      return method.getName() + "(" +
             (CodeStyleSettingsManager.getSettings(method.getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES ? " " : "") +
             ")";
    } else if (o instanceof PsiVariable) {
      return ((PsiVariable)o).getName();
    }
    return null;
  }

  private static THashSet<LookupItem> completeReference(final PsiElement element, final PsiReference reference, final PsiFile originalFile,
                                                        final TailType tailType, final ElementFilter filter, final CompletionResultSet result) {
    final THashSet<LookupItem> set = new THashSet<LookupItem>();
    JavaSmartCompletionContributor.SMART_DATA.completeReference(reference, element, set, tailType, originalFile, filter, new CompletionVariant());
    return set;
  }

  private static class QualifiedMethodLookupItem extends LookupItem<PsiMethod> {
    private final String myQualifier;

    public QualifiedMethodLookupItem(final PsiMethod method, @NotNull final String qualifier) {
      super(method, qualifier + "." + method.getName());
      myQualifier = qualifier;
      addLookupStrings(method.getName());
      setAttribute(JavaCompletionUtil.QUALIFIER_PREFIX_ATTRIBUTE, qualifier + ".");
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) return true;
      if (!(o instanceof QualifiedMethodLookupItem)) return false;
      if (!super.equals(o)) return false;

      final QualifiedMethodLookupItem that = (QualifiedMethodLookupItem)o;

      if (myQualifier != null ? !myQualifier.equals(that.myQualifier) : that.myQualifier != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (myQualifier != null ? myQualifier.hashCode() : 0);
      return result;
    }
  }
}
