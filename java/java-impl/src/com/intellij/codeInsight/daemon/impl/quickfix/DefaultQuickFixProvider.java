// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.lang.java.request.CreateFieldFromUsage;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiFile containingFile = ref.getContainingFile();
    if (containingFile instanceof PsiJavaCodeReferenceCodeFragment &&
        !((PsiJavaCodeReferenceCodeFragment)containingFile).isClassesAccepted()) {
      return;
    }
    if (PsiUtil.isModuleFile(containingFile)) {
      OrderEntryFix.registerFixes(registrar, ref);
      registrar.register(new CreateServiceImplementationClassFix(ref));
      registrar.register(new CreateServiceInterfaceOrClassFix(ref));
      return;
    }

    registrar.register(new ImportClassFix(ref));
    registrar.register(new StaticImportConstantFix(containingFile, ref));
    registrar.register(new QualifyStaticConstantFix(containingFile, ref));

    OrderEntryFix.registerFixes(registrar, ref);

    MoveClassToModuleFix.registerFixes(registrar, ref);

    if (ref instanceof PsiReferenceExpression) {
      TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;

      registrar.register(fixRange, new RenameWrongRefFix(refExpr), null);
      PsiExpression qualifier = ((PsiReferenceExpression)ref).getQualifierExpression();
      if (qualifier != null) {
        AddTypeCastFix.registerFix(registrar, qualifier, ref, fixRange);
      }
      BringVariableIntoScopeFix bringToScope = BringVariableIntoScopeFix.fromReference(refExpr);
      if (bringToScope != null) {
        registrar.register(fixRange, bringToScope, null);
      }

      for (IntentionAction action : createVariableActions(refExpr)) {
        registrar.register(fixRange, action, null);
      }
    }

    registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
    if (PsiUtil.isLanguageLevel5OrHigher(ref)) {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.ANNOTATION));
      registrar.register(new CreateTypeParameterFromUsageFix(ref));
    }
    if (HighlightingFeature.RECORDS.isAvailable(ref)) {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
    }

    PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);
    if (parent instanceof PsiNewExpression &&
        !(ref.getParent() instanceof PsiTypeElement) &&
        (expressionList == null || !PsiTreeUtil.isAncestor(parent, expressionList, false))) {
      registrar.register(new CreateClassFromNewFix((PsiNewExpression)parent));
      registrar.register(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
      if (HighlightingFeature.RECORDS.isAvailable(ref)) {
        registrar.register(new CreateRecordFromNewFix((PsiNewExpression)parent));
        if (((PsiNewExpression)parent).getQualifier() == null) {
          registrar.register(new CreateInnerRecordFromNewFix((PsiNewExpression)parent));
        }
      }
    }
    else {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      registrar.register(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
    }

    SurroundWithQuotesAnnotationParameterValueFix.register(registrar, ref);
  }

  @NotNull
  private static Collection<IntentionAction> createVariableActions(@NotNull PsiReferenceExpression refExpr) {
    final Collection<IntentionAction> result = new ArrayList<>(CreateFieldFromUsage.generateActions(refExpr));
    if (!refExpr.isQualified()) {
      final VariableKind kind = getKind(refExpr);
      IntentionAction createLocalFix = new CreateLocalFromUsageFix(refExpr);
      result.add(kind == VariableKind.LOCAL_VARIABLE ? PriorityIntentionActionWrapper.highPriority(createLocalFix) : createLocalFix);
      IntentionAction createParameterFix = new CreateParameterFromUsageFix(refExpr);
      result.add(kind == VariableKind.PARAMETER ? PriorityIntentionActionWrapper.highPriority(createParameterFix) : createParameterFix);
    }
    return result;
  }

  @Nullable
  private static VariableKind getKind(@NotNull PsiReferenceExpression refExpr) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(refExpr.getProject());
    final String reference = refExpr.getText();

    if (StringUtil.isUpperCase(reference)) {
      return VariableKind.STATIC_FINAL_FIELD;
    }

    for (VariableKind kind : VariableKind.values()) {
      final String prefix = styleManager.getPrefixByVariableKind(kind);
      final String suffix = styleManager.getSuffixByVariableKind(kind);

      if (prefix.isEmpty() && suffix.isEmpty()) {
        continue;
      }

      if (reference.startsWith(prefix) && reference.endsWith(suffix)) {
        return kind;
      }
    }

    if (StringUtil.isCapitalized(reference)) {
      return null;
    }

    return VariableKind.LOCAL_VARIABLE;
  }

  @Override
  @NotNull
  public Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}