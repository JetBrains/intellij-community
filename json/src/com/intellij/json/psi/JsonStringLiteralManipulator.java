// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public final class JsonStringLiteralManipulator extends AbstractElementManipulator<JsonStringLiteral> {

  @Override
  public JsonStringLiteral handleContentChange(@NotNull JsonStringLiteral element, @NotNull TextRange range, String newContent)
    throws IncorrectOperationException {
    assert new TextRange(0, element.getTextLength()).contains(range);

    final String originalContent = element.getText();
    final TextRange withoutQuotes = getRangeInElement(element);
    final JsonElementGenerator generator = new JsonElementGenerator(element.getProject());
    final String replacement =
      StringUtil.unescapeStringCharacters(originalContent.substring(withoutQuotes.getStartOffset(), range.getStartOffset())) +
      newContent +
      StringUtil.unescapeStringCharacters(originalContent.substring(range.getEndOffset(), withoutQuotes.getEndOffset()));
    return (JsonStringLiteral)element.replace(generator.createStringLiteral(replacement));
  }

  @Override
  public @NotNull TextRange getRangeInElement(@NotNull JsonStringLiteral element) {
    final String content = element.getText();
    final int startOffset = content.startsWith("'") || content.startsWith("\"") ? 1 : 0;
    final int endOffset = content.length() > 1 && (content.endsWith("'") || content.endsWith("\"")) ? -1 : 0;
    return new TextRange(startOffset, content.length() + endOffset);
  }
}
