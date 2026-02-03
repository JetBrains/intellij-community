// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.surroundWith;

import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonValue;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JsonSurrounderBase implements Surrounder {
  @Override
  public boolean isApplicable(PsiElement @NotNull [] elements) {
    return elements.length >= 1 && elements[0] instanceof JsonValue && !JsonPsiUtil.isPropertyKey(elements[0]);
  }

  @Override
  public @Nullable TextRange surroundElements(@NotNull Project project, @NotNull Editor editor, PsiElement @NotNull [] elements) {
    if (!isApplicable(elements)) {
      return null;
    }

    final JsonElementGenerator generator = new JsonElementGenerator(project);

    if (elements.length == 1) {
      JsonValue replacement = generator.createValue(createReplacementText(elements[0].getText()));
      elements[0].replace(replacement);
    }
    else {
      final String propertiesText = getTextAndRemoveMisc(elements[0], elements[elements.length - 1]);
      JsonValue replacement = generator.createValue(createReplacementText(propertiesText));
      elements[0].replace(replacement);
    }
    return null;
  }

  protected static @NotNull String getTextAndRemoveMisc(@NotNull PsiElement firstProperty, @NotNull PsiElement lastProperty) {
    final TextRange replacedRange = new TextRange(firstProperty.getTextOffset(), lastProperty.getTextRange().getEndOffset());
    final String propertiesText = replacedRange.substring(firstProperty.getContainingFile().getText());
    if (firstProperty != lastProperty) {
      final PsiElement parent = firstProperty.getParent();
      parent.deleteChildRange(firstProperty.getNextSibling(), lastProperty);
    }
    return propertiesText;
  }

  protected abstract @NotNull String createReplacementText(@NotNull String textInRange);
}
