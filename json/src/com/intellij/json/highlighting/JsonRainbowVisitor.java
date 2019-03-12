// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.highlighting;

import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.json.psi.*;
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
      String name = ((JsonProperty)element).getName();
      PsiFile file = element.getContainingFile();
      addInfo(getInfo(file, ((JsonProperty)element).getNameElement(), name, JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY));
      JsonValue value = ((JsonProperty)element).getValue();
      if (value instanceof JsonObject) {
        addInfo(getInfo(file, value.getFirstChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACES));
        addInfo(getInfo(file, value.getLastChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACES));
      }
      else if (value instanceof JsonArray) {
        addInfo(getInfo(file, value.getFirstChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACKETS));
        addInfo(getInfo(file, value.getLastChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACKETS));
        for (JsonValue jsonValue : ((JsonArray)value).getValueList()) {
          addSimpleValueInfo(name, file, jsonValue);
        }
      }
      else {
        addSimpleValueInfo(name, file, value);
      }
    }
  }

  private void addSimpleValueInfo(String name, PsiFile file, JsonValue value) {
    if (value instanceof JsonStringLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_STRING));
    }
    else if (value instanceof JsonNumberLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_NUMBER));
    }
    else if (value instanceof JsonLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_KEYWORD));
    }
  }

  @NotNull
  @Override
  public HighlightVisitor clone() {
    return new JsonRainbowVisitor();
  }
}
