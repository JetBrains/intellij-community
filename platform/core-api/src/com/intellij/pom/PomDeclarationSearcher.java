// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this interface and register the implementation as {@code com.intellij.pom.declarationSearcher} extension in plugin.xml
 * to provide {@link PomTarget POM targets}.
 *
 * @author peter
 * @see com.intellij.model.psi.PsiSymbolDeclarationProvider
 */
public abstract class PomDeclarationSearcher {

  public static final ExtensionPointName<PomDeclarationSearcher> EP_NAME = ExtensionPointName.create(
    "com.intellij.pom.declarationSearcher"
  );

  /**
   * @param element         potential target host
   * @param offsetInElement offset relative to {@code offset.getTextRange().getStartOffset()}
   * @param consumer        consumer of targets, not thread-safe, only the first target will be used
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  public abstract void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, @NotNull Consumer<PomTarget> consumer);
}
