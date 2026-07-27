// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.analysis.DefaultJavaErrorFixProvider;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated The new fixes should be registered either in {@link DefaultJavaErrorFixProvider} or in
 * {@link com.intellij.codeInspection.AdditionalJavaErrorFixProvider} (if you need more dependencies).
 * In plugins, you may provide your own {@link com.intellij.codeInsight.daemon.impl.analysis.JavaErrorFixProvider} extension.
 */
@Deprecated(forRemoval = true)
public class DefaultQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}