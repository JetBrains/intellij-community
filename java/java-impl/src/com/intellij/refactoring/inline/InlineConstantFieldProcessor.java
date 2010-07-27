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
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;

/**
 * @author ven
 */
class InlineConstantFieldProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineConstantFieldProcessor");
  private PsiField myField;
  private final PsiReferenceExpression myRefExpr;
  private final boolean myInlineThisOnly;

  InlineConstantFieldProcessor(PsiField field, Project project, PsiReferenceExpression ref, boolean isInlineThisOnly) {
    super(project);
    myField = field;
    myRefExpr = ref;
    myInlineThisOnly = isInlineThisOnly;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myField);
  }

  @Override
  protected boolean isPreviewUsages(UsageInfo[] usages) {
    if (super.isPreviewUsages(usages)) return true;
    for (UsageInfo info : usages) {
      if (info instanceof UsageFromJavaDoc) return true;
    }
    return false;
  }

  private static class UsageFromJavaDoc extends UsageInfo {
    private UsageFromJavaDoc(@NotNull PsiElement element) {
      super(element, true);
    }
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myRefExpr)};

    PsiReference[] refs = ReferencesSearch.search(myField, GlobalSearchScope.projectScope(myProject), false).toArray(new PsiReference[0]);
    UsageInfo[] infos = new UsageInfo[refs.length];
    for (int i = 0; i < refs.length; i++) {
      PsiElement element = refs[i].getElement();
      UsageInfo info = new UsageInfo(element);

      if (!(element instanceof PsiExpression) && PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class) == null) {
        info = new UsageFromJavaDoc(element);
      }

      infos[i] = info;
    }
    return infos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
    myField = (PsiField)elements[0];
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);

    PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(myField.getProject()).getConstantEvaluationHelper();
    initializer = normalize ((PsiExpression)initializer.copy());
    for (UsageInfo info : usages) {
      if (info instanceof UsageFromJavaDoc) continue;
      final PsiElement element = info.getElement();
      try {
        if (element instanceof PsiExpression) {
          inlineExpressionUsage((PsiExpression)element, evalHelper, initializer);
        }
        else {
          PsiImportStaticStatement importStaticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
          LOG.assertTrue(importStaticStatement != null, element.getText());
          importStaticStatement.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (!myInlineThisOnly) {
      try {
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void inlineExpressionUsage(PsiExpression expr,
                                     final PsiConstantEvaluationHelper evalHelper,
                                     PsiExpression initializer1) throws IncorrectOperationException {
    while (expr.getParent() instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression)expr.getParent();
      Object value = evalHelper.computeConstantExpression(arrayAccess.getIndexExpression());
      if (value instanceof Integer) {
        int intValue = ((Integer)value).intValue();
        if (initializer1 instanceof PsiNewExpression) {
          PsiExpression[] arrayInitializers = ((PsiNewExpression)initializer1).getArrayInitializer().getInitializers();
          if (0 <= intValue && intValue < arrayInitializers.length) {
            expr = (PsiExpression)expr.getParent();
            initializer1 = normalize(arrayInitializers[intValue]);
            continue;
          }
        }
      }

      break;
    }

    if (initializer1 instanceof PsiArrayInitializerExpression) {
      final PsiType type = expr.getType();
      if (type != null) {
        initializer1 = (PsiExpression)initializer1.replace(
          (PsiNewExpression)JavaPsiFacade.getInstance(expr.getProject()).getElementFactory()
            .createExpressionFromText("new " + type.getCanonicalText() + initializer1.getText(), initializer1));
      }
    }
    myField.normalizeDeclaration();
    ChangeContextUtil.encodeContextInfo(initializer1, true);
    if (expr instanceof PsiReferenceExpression) {
      PsiExpression qExpression = ((PsiReferenceExpression)expr).getQualifierExpression();
      if (qExpression != null) {
        if (initializer1 instanceof PsiMethodCallExpression) {
          PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)initializer1).getMethodExpression();
          if (methodExpression.getQualifierExpression() == null) {
            methodExpression.setQualifierExpression(qExpression);
          }
        } else if (initializer1 instanceof PsiReferenceExpression) {
          PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer1;
          if (referenceExpression.getQualifierExpression() == null) {
            referenceExpression.setQualifierExpression(qExpression);
          }
        }
      }
    }
    PsiElement element = expr.replace(initializer1);
    ChangeContextUtil.decodeContextInfo(element, null, null);
  }

  private static PsiExpression normalize(PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
      try {
        final PsiType type = expression.getType();
        if (type != null) {
          String typeString = type.getCanonicalText();
          PsiNewExpression result = (PsiNewExpression)factory.createExpressionFromText("new " + typeString + "{}", expression);
          result.getArrayInitializer().replace(expression);
          return result;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return expression;
      }
    }

    return expression;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.field.command", UsageViewUtil.getDescriptiveName(myField));
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    MultiMap<PsiElement, String> conflicts = new MultiMap<PsiElement, String>();

    ReferencedElementsCollector collector = new ReferencedElementsCollector();
    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);
    initializer.accept(collector);
    HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myField.getProject()).getResolveHelper();
    for (UsageInfo info : usagesIn) {
      PsiElement element = info.getElement();
      if (element instanceof PsiExpression && isAccessedForWriting((PsiExpression)element)) {
        String message = RefactoringBundle.message("0.is.used.for.writing.in.1", RefactoringUIUtil.getDescription(myField, true),
                                                   RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.putValue(element, message);
      }

      for (PsiMember member : referencedWithVisibility) {
        if (!resolveHelper.isAccessible(member, element, null)) {
          String message = RefactoringBundle.message("0.will.not.be.accessible.from.1.after.inlining", RefactoringUIUtil.getDescription(member, true),
                                                     RefactoringUIUtil.getDescription(ConflictsUtil.getContainer(element), true));
          conflicts.putValue(member, message);
        }
      }
    }

    return showConflicts(conflicts, usagesIn);
  }

  private static boolean isAccessedForWriting (PsiExpression expr) {
    while(expr.getParent() instanceof PsiArrayAccessExpression) {
      expr = (PsiExpression)expr.getParent();
    }

    return PsiUtil.isAccessedForWriting(expr);
  }
}
