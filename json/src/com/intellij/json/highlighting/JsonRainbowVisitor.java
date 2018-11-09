// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.highlighting;

import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class JsonRainbowVisitor extends RainbowVisitor {
  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return file instanceof JsonFile;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (element instanceof JsonProperty) {
      addInfo(getInfo(element.getContainingFile(), element, ((JsonProperty)element).getName(), JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY));
    }
  }

  @NotNull
  @Override
  public HighlightVisitor clone() {
    return new JsonRainbowVisitor();
  }
}
