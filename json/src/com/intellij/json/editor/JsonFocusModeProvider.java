// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.editor;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonFocusModeProvider implements FocusModeProvider {
  @NotNull
  @Override
  public List<? extends Segment> calcFocusZones(@NotNull PsiFile file) {
    return SyntaxTraverser.psiTraverser(file)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof JsonObject ||
                   e instanceof JsonArray)
      .map(e -> e.getTextRange()).toList();

  }
}
