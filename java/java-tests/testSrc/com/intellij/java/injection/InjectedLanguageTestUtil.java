// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.injection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.Objects;

/**
 * The helpers of the tests of injected languages. See {@link InjectedLanguageFixtureKt#injectedLanguageFixture}.
 */
@TestOnly
public final class InjectedLanguageTestUtil {
  private InjectedLanguageTestUtil() {
  }

  /**
   * Returns the PSI tree of each root of {@code file}, with the injected files of each element after the element.
   * Call it in a read action.
   */
  public static @NotNull String toParseTreeText(@NotNull PsiFile file) {
    String result = JBIterable.from(file.getViewProvider().getAllFiles()).map(
        root -> DebugUtil.psiToString(root, true, false, (psiElement, consumer) ->
          InjectedLanguageManager.getInstance(file.getProject()).enumerate(psiElement, (injectedPsi, _) -> consumer.consume(injectedPsi))))
      .reduce(null, (r, o) -> r == null ? o : r + o);
    return Objects.requireNonNull(result);
  }
}
