// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * Provides text in the editor for Evaluate expression action
 *
 * @author Maxim.Medvedev
 */
public interface EditorTextProvider {
  LanguageExtension<EditorTextProvider> EP = new LanguageExtension<>("com.intellij.debuggerEditorTextProvider");

  @Nullable
  TextWithImports getEditorText(PsiElement elementAtCaret);

  @Nullable
  Pair<PsiElement, TextRange> findExpression(PsiElement elementAtCaret, boolean allowMethodCalls);
}
