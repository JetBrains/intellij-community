// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.highlighting;

import com.intellij.codeInsight.daemon.RainbowVisitor;
import com.intellij.codeInsight.daemon.impl.HighlightVisitor;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class JsonRainbowVisitor extends RainbowVisitor {
  private static final class Holder {
    private static final Map<String, Set<String>> blacklist = createBlacklist();

    private static Map<String, Set<String>> createBlacklist() {
      Map<String, Set<String>> blacklist = new HashMap<>();
      blacklist.put("package.json", Set.of("/dependencies",
                                                      "/devDependencies",
                                                      "/peerDependencies",
                                                      "/scripts",
                                                      "/directories",
                                                      "/optionalDependencies"));
      return blacklist;
    }
  }

  @Override
  public boolean suitableForFile(@NotNull PsiFile file) {
    return file instanceof JsonFile;
  }

  @Override
  public void visit(@NotNull PsiElement element) {
    if (element instanceof JsonProperty) {
      PsiFile file = element.getContainingFile();
      String fileName = file.getName();
      if (Holder.blacklist.containsKey(fileName)) {
        JsonPointerPosition position = JsonOriginalPsiWalker.INSTANCE.findPosition(element, false);
        if (position != null && Holder.blacklist.get(fileName).contains(position.toJsonPointer())) return;
      }
      String name = ((JsonProperty)element).getName();
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

  @Override
  public @NotNull HighlightVisitor clone() {
    return new JsonRainbowVisitor();
  }
}
