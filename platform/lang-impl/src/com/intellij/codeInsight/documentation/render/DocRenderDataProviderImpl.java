// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.lang.documentation.InlineDocumentation;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DocRenderDataProviderImpl implements DocRenderDataProvider {
  private static final Key<Collection<DocRenderItem>> OUR_ITEMS = Key.create("doc.render.items");

  @Override
  @Nullable
  public DocRenderItem getDataAroundOffset(@NotNull Editor editor, int offset) {
    Collection<DocRenderItem> items = getItems(editor);
    if (items == null || items.isEmpty()) return null;
    Document document = editor.getDocument();
    if (offset < 0 || offset > document.getTextLength()) return null;
    int line = document.getLineNumber(offset);
    DocRenderItem itemOnAdjacentLine = items.stream().filter(i -> {
      if (!i.isValid()) return false;
      int startLine = document.getLineNumber(i.getHighlighter().getStartOffset());
      int endLine = document.getLineNumber(i.getHighlighter().getEndOffset());
      return line >= startLine - 1 && line <= endLine + 1;
    }).min(Comparator.comparingInt(i -> i.getHighlighter().getStartOffset())).orElse(null);
    if (itemOnAdjacentLine != null) return itemOnAdjacentLine;

    Project project = editor.getProject();
    if (project == null) return null;

    DocRenderItem foundItem = null;
    int foundStartOffset = 0;
    for (DocRenderItem item : items) {
      if (!item.isValid()) continue;
      InlineDocumentation documentation = item.getInlineDocumentation();
      if (documentation == null) continue;
      TextRange ownerTextRange = documentation.getDocumentationOwnerRange();
      if (ownerTextRange == null || !ownerTextRange.containsOffset(offset)) continue;
      int startOffset = ownerTextRange.getStartOffset();
      if (foundItem != null && foundStartOffset >= startOffset) continue;
      foundItem = item;
      foundStartOffset = startOffset;
    }
    return foundItem;
  }

  @Override
  public Collection<DocRenderItem> getItems(@NotNull Editor editor) {
    Collection<DocRenderItem> items;
    Collection<DocRenderItem> existing = editor.getUserData(OUR_ITEMS);
    if (existing == null) {
      editor.putUserData(OUR_ITEMS, items = new ArrayList<>());
    }
    else {
      items = existing;
    }
    return items;
  }
}
