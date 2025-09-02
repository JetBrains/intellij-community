// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JsonFocusModeProvider implements FocusModeProvider {
  @Override
  public @NotNull List<? extends Segment> calcFocusZones(@NotNull PsiFile psiFile) {
    return SyntaxTraverser.psiTraverser(psiFile)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof JsonObject ||
                   e instanceof JsonArray)
      .map(e -> e.getTextRange()).toList();

  }
}
