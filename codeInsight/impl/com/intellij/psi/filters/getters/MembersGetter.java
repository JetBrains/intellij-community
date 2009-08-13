package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.SmartCompletionDecorator;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.TrueFilter;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.04.2003
 * Time: 17:07:09
 * To change this template use Options | File Templates.
 */
public class MembersGetter {

  public static void addMembers(PsiElement position, PsiType expectedType, CompletionResultSet results) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(expectedType);
    if (psiClass != null) {
      processMembers(position, results, psiClass, PsiTreeUtil.getParentOfType(position, PsiAnnotation.class) != null, expectedType);
    }

    if (expectedType instanceof PsiPrimitiveType && PsiType.DOUBLE.isAssignableFrom(expectedType)) {
      final PsiElement parent = position.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiElement refParent = parent.getParent();
        if (refParent instanceof PsiExpressionList) {
          final PsiClass aClass = getCalledClass(refParent.getParent());
          if (aClass != null) {
            processMembers(position, results, aClass, false, expectedType);
          }
        }
        else if (refParent instanceof PsiBinaryExpression) {
          final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)refParent;
          if (parent == binaryExpression.getROperand() &&
              JavaTokenType.EQEQ == binaryExpression.getOperationSign().getTokenType()) {
            final PsiClass aClass = getCalledClass(binaryExpression.getLOperand());
            if (aClass != null) {
              processMembers(position, results, aClass, false, expectedType);
            }
          }
        }
      }
    }
  }

  @Nullable
  private static PsiClass getCalledClass(@Nullable PsiElement call) {
    if (call instanceof PsiMethodCallExpression) {
      for (final JavaResolveResult result : ((PsiMethodCallExpression)call).getMethodExpression().multiResolve(true)) {
        final PsiElement element = result.getElement();
        if (element instanceof PsiMethod) {
          final PsiClass aClass = ((PsiMethod)element).getContainingClass();
          if (aClass != null) {
            return aClass;
          }
        }
      }
    }
    if (call instanceof PsiNewExpression) {
      final PsiJavaCodeReferenceElement reference = ((PsiNewExpression)call).getClassReference();
      if (reference != null) {
        for (final JavaResolveResult result : reference.multiResolve(true)) {
          final PsiElement element = result.getElement();
          if (element instanceof PsiClass) {
            return (PsiClass)element;
          }
        }
      }
    }
    return null;
  }

  private static void processMembers(final PsiElement context, final CompletionResultSet results, final PsiClass where,
                                     final boolean acceptMethods, PsiType expectedType) {
    final FilterScopeProcessor<PsiElement> processor = new FilterScopeProcessor<PsiElement>(TrueFilter.INSTANCE);
    where.processDeclarations(processor, ResolveState.initial(), null, context);

    final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
    for (final PsiElement result : processor.getResults()) {
      if (result instanceof PsiMember && !(result instanceof PsiClass)) {
        final PsiMember member = (PsiMember)result;
        if (member.hasModifierProperty(PsiModifier.STATIC) &&
            !PsiTreeUtil.isAncestor(member.getContainingClass(), context, false) &&
            resolveHelper.isAccessible(member, context, null)) {
          if (result instanceof PsiField && !member.hasModifierProperty(PsiModifier.FINAL)) continue;
          if (result instanceof PsiMethod && acceptMethods) continue;
          final LookupItem item = LookupItemUtil.objectToLookupItem(result);
          item.setAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE);
          JavaCompletionUtil.qualify(item);
          if (member instanceof PsiMethod) {
            item.setAttribute(LookupItem.SUBSTITUTOR, SmartCompletionDecorator.calculateMethodReturnTypeSubstitutor((PsiMethod) member, expectedType));
          }
          results.addElement(item);
        }
      }
    }
  }
}
