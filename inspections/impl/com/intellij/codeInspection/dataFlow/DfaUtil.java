package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Gregory.Shrago
 */
public class DfaUtil {
  private static final Key<CachedValue<MultiValuesMap<PsiVariable, PsiExpression>>> DFA_VARIABLE_INFO_KEY = Key.create("DFA_VARIABLE_INFO_KEY");

  private DfaUtil() {
  }

  public static Collection<PsiExpression> getCachedVariableValues(@Nullable final PsiVariable variable, @Nullable final PsiExpression context) {
    if (variable == null || context == null) return Collections.emptyList();

    PsiCodeBlock codeBlock;
    PsiElement element = context;
    while ((codeBlock = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class)) != null) {
      PsiAnonymousClass anon = PsiTreeUtil.getParentOfType(codeBlock, PsiAnonymousClass.class);
      if (anon == null) break;
      element = anon;
    }
    if (codeBlock == null) return Collections.emptyList();
    final PsiCodeBlock topLevelBlock = codeBlock;

    CachedValue<MultiValuesMap<PsiVariable, PsiExpression>> cachedValue = context.getUserData(DFA_VARIABLE_INFO_KEY);
    if (cachedValue == null) {
      cachedValue = context.getManager().getCachedValuesManager().createCachedValue(new CachedValueProvider<MultiValuesMap<PsiVariable, PsiExpression>>() {
        public Result<MultiValuesMap<PsiVariable, PsiExpression>> compute() {
          final ValuableDataFlowRunner runner = new ValuableDataFlowRunner(context);
          final MultiValuesMap<PsiVariable, PsiExpression> result;
          if (runner.analyzeMethod(topLevelBlock, new StandardInstructionVisitor()) == RunnerResult.OK) {
            result = runner.getAllVariableValues();
          }
          else {
            result = null;
          }
          return new Result<MultiValuesMap<PsiVariable, PsiExpression>>(result, topLevelBlock);
        }
      }, false);
      context.putUserData(DFA_VARIABLE_INFO_KEY, cachedValue);
    }
    final MultiValuesMap<PsiVariable, PsiExpression> value = cachedValue.getValue();
    final Collection<PsiExpression> expressions = value == null ? null : value.get(variable);
    return expressions == null ? Collections.<PsiExpression>emptyList() : expressions;
  }

  public static Collection<? extends PsiElement> getPossibleInitializationElements(final PsiElement qualifierExpression) {
    if (qualifierExpression instanceof PsiMethodCallExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    else if (qualifierExpression instanceof PsiReferenceExpression) {
      final PsiElement targetElement = ((PsiReferenceExpression)qualifierExpression).resolve();
      if (targetElement instanceof PsiVariable) {
        final Collection<? extends PsiElement> variableValues = getCachedVariableValues((PsiVariable)targetElement, (PsiExpression)qualifierExpression);
        if (variableValues.isEmpty() && targetElement instanceof PsiField) {
          return getVariableAssignmentsInFile((PsiVariable)targetElement, false);
        }
        return variableValues;
      }
    }
    else if (qualifierExpression instanceof PsiLiteralExpression) {
      return Collections.singletonList(qualifierExpression);
    }
    return Collections.emptyList();
  }

  public static Collection<PsiExpression> getVariableAssignmentsInFile(final PsiVariable psiVariable, final boolean literalsOnly) {
    final Ref<Boolean> modificationRef = Ref.create(Boolean.FALSE);
    final List<PsiExpression> list = ContainerUtil.mapNotNull(
      ReferencesSearch.search(psiVariable, new LocalSearchScope(new PsiElement[] {psiVariable.getContainingFile()}, null, true)).findAll(),
      new NullableFunction<PsiReference, PsiExpression>() {
        public PsiExpression fun(final PsiReference psiReference) {
          if (modificationRef.get()) return null;
          final PsiElement parent = psiReference.getElement().getParent();
          if (parent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
            final IElementType operation = assignmentExpression.getOperationTokenType();
            if (assignmentExpression.getLExpression() == psiReference) {
              if (JavaTokenType.EQ.equals(operation)) {
                if (!literalsOnly || allOperandsAreLiterals(assignmentExpression.getRExpression())) {
                  return assignmentExpression.getRExpression();
                }
                else {
                  modificationRef.set(Boolean.TRUE);
                }
              }
              else if (JavaTokenType.PLUSEQ.equals(operation)) {
                modificationRef.set(Boolean.TRUE);
              }
            }
          }
          return null;
        }
      });
    if (modificationRef.get()) return Collections.emptyList();
    if (!literalsOnly || allOperandsAreLiterals(psiVariable.getInitializer())) {
      ContainerUtil.addIfNotNull(psiVariable.getInitializer(), list);
    }
    return list;
  }

  public static boolean allOperandsAreLiterals(@Nullable final PsiExpression expression) {
    if (expression == null) return false;
    if (expression instanceof PsiLiteralExpression) return true;
    if (expression instanceof PsiBinaryExpression) {
      final LinkedList<PsiExpression> stack = new LinkedList<PsiExpression>();
      stack.add(expression);
      while (!stack.isEmpty()) {
        final PsiExpression psiExpression = stack.removeFirst();
        if (psiExpression instanceof PsiBinaryExpression) {
          final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)psiExpression;
          stack.addLast(binaryExpression.getLOperand());
          final PsiExpression right = binaryExpression.getROperand();
          if (right != null) {
            stack.addLast(right);
          }
        }
        else if (!(psiExpression instanceof PsiLiteralExpression)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}
