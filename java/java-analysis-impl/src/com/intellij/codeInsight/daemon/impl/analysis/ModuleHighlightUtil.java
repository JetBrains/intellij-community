// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.*;
import com.intellij.psi.impl.IncompleteModelUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// generates HighlightInfoType.ERROR-like HighlightInfos for modularity-related (Jigsaw) problems
final class ModuleHighlightUtil {

  static HighlightInfo.Builder checkModuleReference(@NotNull PsiImportModuleStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();
    PsiJavaModule target = ref.resolve();
    if (target == null) return getUnresolvedJavaModuleReason(statement, refElement);
    for (JavaModuleSystem moduleSystem : JavaModuleSystem.EP_NAME.getExtensionList()) {
      if (!(moduleSystem instanceof JavaModuleSystemEx javaModuleSystemEx)) continue;
      JavaModuleSystemEx.ErrorWithFixes fixes = javaModuleSystemEx.checkAccess(target, statement);
      if (fixes == null) continue;
      HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(statement)
        .descriptionAndTooltip(fixes.message);
      fixes.fixes.forEach(fix -> info.registerFix(fix, null, null, null, null));
      return info;
    }
    return null;
  }

  private static @NotNull HighlightInfo.Builder getUnresolvedJavaModuleReason(@NotNull PsiElement parent, @NotNull PsiJavaModuleReferenceElement refElement) {
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();

    ResolveResult[] results = ref.multiResolve(true);
    switch (results.length) {
      case 0:
        if (IncompleteModelUtil.isIncompleteModel(parent)) {
          return HighlightUtil.getPendingReferenceHighlightInfo(refElement);
        } else {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
            .range(refElement)
            .descriptionAndTooltip(JavaErrorBundle.message("module.not.found", refElement.getReferenceText()));
        }
      case 1:
        String message = JavaErrorBundle.message("module.not.on.path", refElement.getReferenceText());
        HighlightInfo.Builder info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
          .range(refElement)
          .descriptionAndTooltip(message);
        List<IntentionAction> registrar = new ArrayList<>();
        QuickFixFactory.getInstance().registerOrderEntryFixes(ref, registrar);
        QuickFixAction.registerQuickFixActions(info, null, registrar);
        return info;
      default:
        return HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
          .range(refElement)
          .descriptionAndTooltip(JavaErrorBundle.message("module.ambiguous", refElement.getReferenceText()));
    }
  }
}