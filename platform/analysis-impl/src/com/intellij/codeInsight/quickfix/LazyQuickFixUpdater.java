// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


/**
 * Call each registered {@link UnresolvedReferenceQuickFixProvider} for its quick fixes.
 * It does roughly the same as {@link UnresolvedReferenceQuickFixProvider#registerReferenceFixes(PsiReference, QuickFixActionRegistrar)}, except:
 * 1) each {@link UnresolvedReferenceQuickFixProvider#registerFixes(PsiReference, QuickFixActionRegistrar)} is called in the background and
 * 2) in the lazy manner (no more than two providers in parallel).
 * That way we make highlighting to complete faster (because fewer {@link UnresolvedReferenceQuickFixProvider}s are called)
 * and avoid freezes (because dozens of providers are not run at the same time, taking resources unnecessarily)
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface LazyQuickFixUpdater {
  static LazyQuickFixUpdater getInstance(Project project) {
    return project.getService(LazyQuickFixUpdater.class);
  }

  /**
   * Wait until the background calculation of unresolved reference quickfixes for {@code info} is completed.
   * This method might be needed when that information is required synchronously, e.g., when user pressed Alt-Enter.
   */
  @RequiresBackgroundThread
  void waitQuickFixesSynchronously(@NotNull PsiFile file, @NotNull Editor editor, @NotNull HighlightInfo info);

  /**
   * Start background computation of quick fixes for unresolved references in the {@code file} at the current caret offset
   */
  @RequiresBackgroundThread
  @RequiresReadLock
  void startComputingNextQuickFixes(@NotNull PsiFile file, @NotNull Editor editor, @NotNull ProperTextRange visibleRange);
}
