// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.JavaModuleSystemEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

// generates HighlightInfoType.ERROR-like HighlightInfos for modularity-related (Jigsaw) problems
final class ModuleHighlightUtil {

  static HighlightInfo.Builder checkModuleReferenceAccess(@NotNull PsiImportModuleStatement statement) {
    PsiJavaModuleReferenceElement refElement = statement.getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    assert ref != null : refElement.getParent();
    PsiJavaModule target = ref.resolve();
    if (target == null) return null;
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
}