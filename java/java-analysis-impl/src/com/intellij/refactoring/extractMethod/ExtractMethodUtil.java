// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.RedundantCastUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ven
 */
public final class ExtractMethodUtil {
  private static final Logger LOG = Logger.getInstance(ExtractMethodUtil.class);
  private static final Key<PsiMethod> RESOLVE_TARGET_KEY = Key.create("RESOLVE_TARGET_KEY");

  private ExtractMethodUtil() { }

  static Map<PsiMethodCallExpression, PsiMethod> encodeOverloadTargets(final PsiClass targetClass,
                                                        final SearchScope processConflictsScope,
                                                        final String overloadName,
                                                        final PsiElement extractedFragment) {
    final Map<PsiMethodCallExpression, PsiMethod> ret = new HashMap<>();
    encodeInClass(targetClass, overloadName, extractedFragment, ret);

    ClassInheritorsSearch.search(targetClass, processConflictsScope, true).allowParallelProcessing().forEach(inheritor -> {
      encodeInClass(inheritor, overloadName, extractedFragment, ret);
      return true;
    });

    return ret;
  }

  private static void encodeInClass(final PsiClass aClass,
                                    final String overloadName,
                                    final PsiElement extractedFragment,
                                    final Map<PsiMethodCallExpression, PsiMethod> ret) {
    final PsiMethod[] overloads = aClass.findMethodsByName(overloadName, false);
    for (final PsiMethod overload : overloads) {
      for (final PsiReference ref : ReferencesSearch.search(overload)) {
        final PsiElement element = ref.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression call = (PsiMethodCallExpression)parent;
          if (PsiTreeUtil.isAncestor(extractedFragment, element, false)) {
            call.putCopyableUserData(RESOLVE_TARGET_KEY, overload);
          } else {
            //we assume element won't be invalidated as a result of extraction
            ret.put(call, overload);
          }
        }
      }
    }
  }

  public static void decodeOverloadTargets(Map<PsiMethodCallExpression, PsiMethod> oldResolves, final PsiMethod extracted,
                                           final PsiElement oldFragment) {
    final PsiCodeBlock body = extracted.getBody();
    assert body != null;
    final JavaRecursiveElementVisitor visitor = new JavaRecursiveElementVisitor() {

      @Override public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiMethod target = expression.getCopyableUserData(RESOLVE_TARGET_KEY);
        if (target != null) {
          expression.putCopyableUserData(RESOLVE_TARGET_KEY, null);
          try {
            addCastsToEnsureResolveTarget(target, expression);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    };
    body.accept(visitor);
    oldFragment.accept(visitor);

    for (final Map.Entry<PsiMethodCallExpression, PsiMethod> entry : oldResolves.entrySet()) {
      try {
        addCastsToEnsureResolveTarget(entry.getValue(), entry.getKey());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  public static void addCastsToEnsureResolveTarget(@NotNull final PsiMethod oldTarget, @NotNull final PsiMethodCallExpression call)
    throws IncorrectOperationException {
    final PsiMethod newTarget = call.resolveMethod();
    final PsiManager manager = oldTarget.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    if (!manager.areElementsEquivalent(oldTarget, newTarget)) {
      final PsiParameter[] oldParameters = oldTarget.getParameterList().getParameters();
      if (oldParameters.length > 0) {
        final PsiMethodCallExpression copy = (PsiMethodCallExpression)call.copy();
        PsiExpression[] args = copy.getArgumentList().getExpressions();
        for (int i = 0; i < args.length; i++) {
          PsiExpression arg = args[i];
          PsiType paramType = i < oldParameters.length ? oldParameters[i].getType() : oldParameters[oldParameters.length - 1].getType();
          final PsiTypeCastExpression cast = (PsiTypeCastExpression)factory.createExpressionFromText("(a)b", null);
          final PsiTypeElement typeElement = cast.getCastType();
          assert typeElement != null;
          typeElement.replace(factory.createTypeElement(paramType));
          final PsiExpression operand = cast.getOperand();
          assert operand != null;
          operand.replace(arg);
          arg.replace(cast);
        }

        args = copy.getArgumentList().getExpressions();
        PsiExpression[] oldArgs = call.getArgumentList().getExpressions();
        for (int i = 0; i < args.length; i++) {
          PsiExpression oldArg = oldArgs[i];
          PsiTypeCastExpression cast = (PsiTypeCastExpression)args[i];
          if (!RedundantCastUtil.isCastRedundant(cast)) {
            oldArg.replace(cast);
          }
        }
      }
    }
  }
}
