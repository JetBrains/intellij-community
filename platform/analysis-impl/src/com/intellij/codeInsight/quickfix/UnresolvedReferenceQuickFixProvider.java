// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiReference;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Register implementation of this class as 'com.intellij.codeInsight.unresolvedReferenceQuickFixProvider' extension to provide additional
 * quick fixes for 'Unresolved reference' problems.
 *
 * @param <T> type of element you want register quick fixes for; for example, in Java language it may be {@link com.intellij.psi.PsiJavaCodeReferenceElement}
 */
public abstract class UnresolvedReferenceQuickFixProvider<T extends PsiReference> {
  public static <T extends PsiReference> void registerReferenceFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar) {
    final boolean dumb = DumbService.getInstance(ref.getElement().getProject()).isDumb();
    Class<? extends PsiReference> referenceClass = ref.getClass();
    for (UnresolvedReferenceQuickFixProvider<?> each : EXTENSION_NAME.getExtensionList()) {
      if (dumb && !DumbService.isDumbAware(each)) {
        continue;
      }
      if (ReflectionUtil.isAssignable(each.getReferenceClass(), referenceClass)) {
        //noinspection unchecked
        ((UnresolvedReferenceQuickFixProvider<T>)each).registerFixes(ref, registrar);
      }
    }
  }

  private static final ExtensionPointName<UnresolvedReferenceQuickFixProvider<?>> EXTENSION_NAME = ExtensionPointName.create("com.intellij.codeInsight.unresolvedReferenceQuickFixProvider");

  public abstract void registerFixes(@NotNull T ref, @NotNull QuickFixActionRegistrar registrar);

  @NotNull
  public abstract Class<T> getReferenceClass();
}
