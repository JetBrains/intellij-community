// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class RenameWrongRefFix implements IntentionAction, LowPriorityAction {
  private final PsiReferenceExpression myRefExpr;
  private static final @NonNls String INPUT_VARIABLE_NAME = "INPUTVAR";
  private static final @NonNls String OTHER_VARIABLE_NAME = "OTHERVAR";
  private final boolean myUnresolvedOnly;
  private @NotNull @Nls String myText = QuickFixBundle.message("rename.wrong.reference.text");

  public RenameWrongRefFix(@NotNull PsiReferenceExpression refExpr) {
    this(refExpr, false);
  }

  public RenameWrongRefFix(@NotNull PsiReferenceExpression refExpr, boolean unresolvedOnly) {
    myRefExpr = refExpr;
    myUnresolvedOnly = unresolvedOnly;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new RenameWrongRefFix(PsiTreeUtil.findSameElementInCopy(myRefExpr, target), myUnresolvedOnly);
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("rename.wrong.reference.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myRefExpr.isValid() || !BaseIntentionAction.canModify(myRefExpr)) return false;
    PsiElement refName = myRefExpr.getReferenceNameElement();
    if (refName == null) return false;
    PsiExpression qualifier = myRefExpr.getQualifierExpression();
    if (qualifier != null && TypeConversionUtil.isPrimitiveAndNotNull(qualifier.getType())) {
      PsiExpression expression = (myRefExpr.getParent() instanceof PsiMethodCallExpression call) ? call : myRefExpr;
      if (ExpressionUtils.isVoidContext(expression)) {
        return false;
      }
      myText = QuickFixBundle.message("replace.with.qualifier.text");
    }
    else {
      myText = QuickFixBundle.message("rename.wrong.reference.text");
    }
    return !CreateFromUsageUtils.isValidReference(myRefExpr, myUnresolvedOnly);
  }

  private LookupElement @NotNull [] collectItems() {
    Set<LookupElement> items = new LinkedHashSet<>();
    boolean qualified = myRefExpr.getQualifierExpression() != null;

    if (!qualified && !(myRefExpr.getParent() instanceof PsiMethodCallExpression)) {
      PsiVariable[] vars = CreateFromUsageUtils.guessMatchingVariables(myRefExpr);
      for (PsiVariable var : vars) {
        items.add(createLookupElement(var, v-> v.getName()));
      }
    } else {
      class MyScopeProcessor implements PsiScopeProcessor {
        private final Map<String, PsiElement> myResult = new HashMap<>();
        private final boolean myFilterMethods;
        private final boolean myFilterStatics;

        MyScopeProcessor(PsiReferenceExpression refExpression) {
          myFilterMethods = refExpression.getParent() instanceof PsiMethodCallExpression;
          PsiExpression qualifier = refExpression.getQualifierExpression();
          if (qualifier instanceof PsiReferenceExpression) {
            PsiElement e = ((PsiReferenceExpression) qualifier).resolve();
            myFilterStatics = e instanceof PsiClass;
          } else if (qualifier == null) {
            PsiModifierListOwner scope = PsiTreeUtil.getParentOfType(refExpression, PsiModifierListOwner.class);
            myFilterStatics = scope != null && scope.hasModifierProperty(PsiModifier.STATIC);
          }
          else {
            myFilterStatics = false;
          }
        }

        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
          if (element instanceof PsiNamedElement
              && element instanceof PsiModifierListOwner
              && myFilterMethods == element instanceof PsiMethod
              && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.STATIC) == myFilterStatics
              && isAccessible(element)) {
            myResult.put(((PsiNamedElement)element).getName(), element);
          }
          return true;
        }

        private boolean isAccessible(PsiElement element) {
          if (!(element instanceof PsiCompiledElement) || !(element instanceof PsiMember)) return true;
          final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(element.getProject()).getResolveHelper();
          return resolveHelper.isAccessible((PsiMember)element, myRefExpr, null);
        }

        public PsiElement[] getVariants () {
          return PsiUtilCore.toPsiElementArray(myResult.values());
        }
      }

      MyScopeProcessor processor = new MyScopeProcessor(myRefExpr);
      myRefExpr.processVariants(processor);
      PsiElement[] variants = processor.getVariants();
      for (PsiElement variant : variants) {
        items.add(createLookupElement((PsiNamedElement)variant, v -> v.getName()));
      }
    }

    return items.toArray(LookupElement.EMPTY_ARRAY);
  }

  private static @NotNull <T extends PsiElement> LookupElementBuilder createLookupElement(T variant, Function<? super T, String> toPresentableElement) {
    return LookupElementBuilder.create(variant, toPresentableElement.apply(variant));
  }

  /**
   * Note that this method also called from rename handler {@link com.intellij.refactoring.rename.RenameWrongRefHandler}
   */
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiExpression qualifier = myRefExpr.getQualifierExpression();

    PsiExpression expression = (myRefExpr.getParent() instanceof PsiMethodCallExpression call) ? call : myRefExpr;
    if (qualifier != null && TypeConversionUtil.isPrimitiveAndNotNull(qualifier.getType()) && !ExpressionUtils.isVoidContext(expression)) {
      new CommentTracker().replaceAndRestoreComments(expression, qualifier);
      return;
    }

    PsiReferenceExpression[] refs = CreateFromUsageUtils.collectExpressions(myRefExpr, PsiMember.class, PsiFile.class);
    PsiElement element = PsiTreeUtil.getParentOfType(myRefExpr, PsiMember.class, PsiFile.class);
    if (element == null) return;
    LookupElement[] items = collectItems();
    ReferenceNameExpression refExpr = new ReferenceNameExpression(items, myRefExpr.getReferenceName());

    TemplateBuilder builder = new TemplateBuilderImpl(element);
    for (PsiReferenceExpression expr : refs) {
      if (!expr.equals(myRefExpr)) {
        builder.replaceElement(expr.getReferenceNameElement(), OTHER_VARIABLE_NAME, INPUT_VARIABLE_NAME, false);
      }
      else {
        builder.replaceElement(expr.getReferenceNameElement(), INPUT_VARIABLE_NAME, refExpr, true);
      }
    }
    final float proportion = EditorUtil.calcVerticalScrollProportion(editor);
    builder.run(editor, true);
    if (file.isPhysical()) {
      EditorUtil.setVerticalScrollProportion(editor, proportion);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
