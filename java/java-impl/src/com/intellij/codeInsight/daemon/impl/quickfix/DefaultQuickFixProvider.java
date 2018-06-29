// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMethodUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.lang.java.request.CreateFieldFromUsage;
import com.intellij.lang.jvm.actions.JvmElementActionFactories;
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
import java.util.EnumMap;
import java.util.Map;

public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (PsiUtil.isModuleFile(ref.getContainingFile())) {
      OrderEntryFix.registerFixes(registrar, ref);
      registrar.register(new CreateServiceImplementationClassFix(ref));
      registrar.register(new CreateServiceInterfaceOrClassFix(ref));
      return;
    }

    QuickFixFactory quickFixFactory = QuickFixFactory.getInstance();
    registrar.register(new ImportClassFix(ref));
    registrar.register(new StaticImportConstantFix(ref));
    registrar.register(new QualifyStaticConstantFix(ref));
    registrar.register(quickFixFactory.createSetupJDKFix());

    OrderEntryFix.registerFixes(registrar, ref);

    MoveClassToModuleFix.registerFixes(registrar, ref);

    if (ref instanceof PsiReferenceExpression) {
      TextRange fixRange = HighlightMethodUtil.getFixRange(ref);
      PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;

      registrar.register(new RenameWrongRefFix(refExpr));
      if (!ref.isQualified()) {
        registrar.register(fixRange, new BringVariableIntoScopeFix(refExpr), null);
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

    PsiElement parent = PsiTreeUtil.getParentOfType(ref, PsiNewExpression.class, PsiMethod.class);
    PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(ref, PsiExpressionList.class);
    if (parent instanceof PsiNewExpression &&
        !(ref.getParent() instanceof PsiTypeElement) &&
        (expressionList == null || !PsiTreeUtil.isAncestor(parent, expressionList, false))) {
      registrar.register(new CreateClassFromNewFix((PsiNewExpression)parent));
      registrar.register(new CreateInnerClassFromNewFix((PsiNewExpression)parent));
    }
    else {
      registrar.register(new CreateClassFromUsageFix(ref, CreateClassKind.CLASS));
      registrar.register(new CreateInnerClassFromUsageFix(ref, CreateClassKind.CLASS));
    }

    SurroundWithQuotesAnnotationParameterValueFix.register(registrar, ref);
  }

  @NotNull
  private static Collection<IntentionAction> createVariableActions(@NotNull PsiReferenceExpression refExpr) {
    final Collection<IntentionAction> result = new ArrayList<>();

    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(refExpr.getProject());
    final VariableKind kind = getKind(styleManager, refExpr) ;

    if (JvmElementActionFactories.useInterlaguageActions()) {
      result.addAll(CreateFieldFromUsage.generateActions(refExpr));
      if (!refExpr.isQualified()) {
        IntentionAction createLocalFix = new CreateLocalFromUsageFix(refExpr);
        result.add(kind == VariableKind.LOCAL_VARIABLE ? PriorityIntentionActionWrapper.highPriority(createLocalFix) : createLocalFix);
        IntentionAction createParameterFix = new CreateParameterFromUsageFix(refExpr);
        result.add(kind == VariableKind.PARAMETER ? PriorityIntentionActionWrapper.highPriority(createParameterFix) : createParameterFix);
      }
      return result;
    }

    final Map<VariableKind, IntentionAction> map = new EnumMap<>(VariableKind.class);
    map.put(VariableKind.FIELD, new CreateFieldFromUsageFix(refExpr));
    map.put(VariableKind.STATIC_FINAL_FIELD, new CreateConstantFieldFromUsageFix(refExpr));
    if (!refExpr.isQualified()) {
      map.put(VariableKind.LOCAL_VARIABLE, new CreateLocalFromUsageFix(refExpr));
      map.put(VariableKind.PARAMETER, new CreateParameterFromUsageFix(refExpr));
    }

    if (map.containsKey(kind)) {
      map.put(kind, PriorityIntentionActionWrapper.highPriority(map.get(kind)));
    }

    result.add(new CreateEnumConstantFromUsageFix(refExpr));
    result.addAll(map.values());
    return result;
  }

  @Nullable
  private static VariableKind getKind(@NotNull JavaCodeStyleManager styleManager, @NotNull PsiReferenceExpression refExpr) {
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