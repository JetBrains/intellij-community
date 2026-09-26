// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.ExpectedTypesProvider;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JavaFoldingBuilder extends IdeJavaFoldingBuilderBase {

  @Override
  protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors,
                                          @NotNull PsiElement root,
                                          @NotNull Document document,
                                          boolean quick) {
    if (!(root instanceof PsiJavaFile file)) return;
    if (Registry.is("java.folding.split.frontend.backend")) {
      // New behavior: only backend foldings (frontend builder handles the rest)
      JavaBackendFoldings.buildBackendFoldRegions(this, descriptors, file, document, quick);
    }
    else {
      // Old behavior: all foldings, skip on JBC
      if (PlatformUtils.isJetBrainsClient()) return;
      JavaFrontendFoldings.buildFrontendFoldRegions(descriptors, file, document);
      JavaBackendFoldings.buildBackendFoldRegions(this, descriptors, file, document, quick);
    }
  }

  @Override
  protected boolean shouldShowExplicitLambdaType(@NotNull PsiAnonymousClass anonymousClass, @NotNull PsiNewExpression expression) {
    if (super.shouldShowExplicitLambdaType(anonymousClass, expression)) {
      return true;
    }
    ExpectedTypeInfo[] types = ExpectedTypesProvider.getExpectedTypes(expression, false);
    return types.length != 1 || !types[0].getType().equals(anonymousClass.getBaseClassType());
  }
}

