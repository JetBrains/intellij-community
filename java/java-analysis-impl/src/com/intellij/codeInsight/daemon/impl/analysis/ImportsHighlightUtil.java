// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ImportsHighlightUtil {
  public static final Key<Set<String>> IMPORTS_FROM_TEMPLATE = Key.create("IMPORT_FROM_FILE_TEMPLATE");

  static HighlightInfo checkStaticOnDemandImportResolvesToClass(@NotNull PsiImportStaticStatement statement) {
    if (statement.isOnDemand() && statement.resolveTargetClass() == null) {
      PsiJavaCodeReferenceElement ref = statement.getImportReference();
      if (ref != null) {
        PsiElement resolve = ref.resolve();
        if (resolve != null) {
          return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .range(ObjectUtils.notNull(ref.getReferenceNameElement(), ref))
            .descriptionAndTooltip("Class " + ref.getCanonicalText() + " not found").create();
        }
      }
    }
    return null;
  }
}
