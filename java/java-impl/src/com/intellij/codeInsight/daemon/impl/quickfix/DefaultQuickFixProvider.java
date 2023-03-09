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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    PsiFile containingFile = ref.getContainingFile();
    if (containingFile instanceof PsiJavaCodeReferenceCodeFragment fragment && !fragment.isClassesAccepted()) {
      return;
    }
    List<IntentionAction> fixes = new ArrayList<>();
    OrderEntryFix.registerFixes(ref, fixes);
    for (IntentionAction fix : fixes) {
      registrar.register(fix);
    }
    if (PsiUtil.isModuleFile(containingFile)) {
      registrar.register(new CreateServiceImplementationClassFix(ref));
      registrar.register(new CreateServiceInterfaceOrClassFix(ref));
      return;
    }

    registrar.register(new ImportClassFix(ref));
    PsiElement refParent = ref.getParent();
    if (!(refParent instanceof PsiMethodCallExpression)) {
      registrar.register(new StaticImportConstantFix(containingFile, ref));
      registrar.register(new QualifyStaticConstantFix(containingFile, ref));
    }

    MoveClassToModuleFix.registerFixes(registrar, ref);

    if (ref instanceof PsiReferenceExpression refExpr) {
      TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
      registrar.register(fixRange, new RenameWrongRefFix(refExpr), null);
      PsiExpression qualifier = refExpr.getQualifierExpression();
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

    for (IntentionAction action : createClassActions(ref)) {
      registrar.register(action);
    }

    SurroundWithQuotesAnnotationParameterValueFix.register(registrar, ref);

    if (PsiUtil.isLanguageLevel5OrHigher(ref)) {
      registrar.register(new CreateTypeParameterFromUsageFix(ref));
    }
  }

  @NotNull
  private static Collection<IntentionAction> createClassActions(@NotNull PsiJavaCodeReferenceElement ref) {
    Collection<IntentionAction> result = new ArrayList<>();
    PsiElement refParent = ref.getParent();
    if (refParent != null && refParent.getParent() instanceof PsiDeconstructionPattern) {
      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
      result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.RECORD));
    }
    else {
      PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
      PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);

      boolean isNewExpression =
        parent instanceof PsiNewExpression &&
        !(refParent instanceof PsiTypeElement) &&
        (expressionList == null || !PsiTreeUtil.isAncestor(parent, expressionList, false));

      if (isNewExpression) {
        result.add(new CreateClassFromNewFix((PsiNewExpression)parent));
      }
      else {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      }

      result.add(new CreateClassFromUsageFix(ref, CreateClassKind.INTERFACE));
      if (PsiUtil.isLanguageLevel5OrHigher(ref)) {
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ENUM));
        result.add(new CreateClassFromUsageFix(ref, CreateClassKind.ANNOTATION));
      }
      if (HighlightingFeature.RECORDS.isAvailable(ref)) {
        if (isNewExpression) {
          result.add(new CreateRecordFromNewFix((PsiNewExpression)parent));
        }
        else {
          result.add(new CreateClassFromUsageFix(ref, CreateClassKind.RECORD));
        }
      }

      if (isNewExpression) {
        result.add(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
        if (HighlightingFeature.RECORDS.isAvailable(ref) && ((PsiNewExpression)parent).getQualifier() == null) {
          result.add(new CreateInnerRecordFromNewFix((PsiNewExpression)parent));
        }
      }
      else {
        result.add(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
      }
    }
    return result;
  }

  @NotNull
  private static Collection<IntentionAction> createVariableActions(@NotNull PsiReferenceExpression refExpr) {
    Collection<IntentionAction> result = new ArrayList<>();
    boolean isQualified = refExpr.isQualified();
    VariableKind kind = getKind(refExpr);

    if (!isQualified) {
      IntentionAction createLocalFix = new CreateLocalFromUsageFix(refExpr);
      result.add(kind == VariableKind.LOCAL_VARIABLE ? PriorityIntentionActionWrapper.highPriority(createLocalFix) : createLocalFix);
    }

    List<IntentionAction> createFieldFixes = CreateFieldFromUsage.generateActions(refExpr);
    if (kind == VariableKind.FIELD) {
      createFieldFixes = ContainerUtil.map(createFieldFixes, fix -> PriorityIntentionActionWrapper.highPriority(fix));
    }
    result.addAll(createFieldFixes);

    if (!isQualified) {
      IntentionAction createParameterFix = new CreateParameterFromUsageFix(refExpr);
      result.add(kind == VariableKind.PARAMETER ? PriorityIntentionActionWrapper.highPriority(createParameterFix) : createParameterFix);
    }

    return result;
  }

  @Nullable
  private static VariableKind getKind(@NotNull PsiReferenceExpression refExpr) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(refExpr.getProject());
    String reference = refExpr.getText();

    if (StringUtil.isUpperCase(reference)) {
      return VariableKind.STATIC_FINAL_FIELD;
    }

    for (VariableKind kind : VariableKind.values()) {
      String prefix = styleManager.getPrefixByVariableKind(kind);
      String suffix = styleManager.getSuffixByVariableKind(kind);

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