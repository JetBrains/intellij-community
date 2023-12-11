// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The UnhandledExceptionFixProvider interface provides a way to register fixes for unhandled exceptions.
 */
public interface UnhandledExceptionFixProvider {
  ExtensionPointName<UnhandledExceptionFixProvider> EP_NAME = new ExtensionPointName<>("com.intellij.unhandledExceptionFixProvider");

  /**
   * Registers fixes for unhandled exceptions.
   * <p>
   * This method is responsible for registering fixes for unhandled exceptions in the provided {@link HighlightInfo.Builder} instance.
   *
   * @param info                the {@link HighlightInfo.Builder} instance to register the fixes into
   * @param element             the {@link PsiElement} representing the location of the unhandled exception
   * @param unhandledExceptions the list of unhandled exception types
   */
  void registerUnhandledExceptionFixes(@NotNull HighlightInfo.Builder info, @NotNull PsiElement element,
                                       @NotNull List<PsiClassType> unhandledExceptions);
}
