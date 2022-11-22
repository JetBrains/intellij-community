// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.lang.documentation.InlineDocumentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BooleanSupplier;

public class DocRenderItemManagerImpl implements DocRenderItemManager {
  @Topic.AppLevel
  public static final Topic<DocRenderItemImpl.Listener> TOPIC = new Topic<>(DocRenderItemImpl.Listener.class, Topic.BroadcastDirection.NONE, true);

  private static final Key<Collection<DocRenderItemImpl>> OUR_ITEMS = Key.create("doc.render.items");
  static final Key<Boolean> OWN_HIGHLIGHTER = Key.create("doc.render.highlighter");

  @Override
  @Nullable
  public DocRenderItemImpl getItemAroundOffset(@NotNull Editor editor, int offset) {
    Collection<DocRenderItemImpl> items = editor.getUserData(OUR_ITEMS);
    if (items == null || items.isEmpty()) return null;
    Document document = editor.getDocument();
    if (offset < 0 || offset > document.getTextLength()) return null;
    int line = document.getLineNumber(offset);
    DocRenderItemImpl itemOnAdjacentLine = items.stream().filter(i -> {
      if (!i.isValid()) return false;
      int startLine = document.getLineNumber(i.getHighlighter().getStartOffset());
      int endLine = document.getLineNumber(i.getHighlighter().getEndOffset());
      return line >= startLine - 1 && line <= endLine + 1;
    }).min(Comparator.comparingInt(i -> i.getHighlighter().getStartOffset())).orElse(null);
    if (itemOnAdjacentLine != null) return itemOnAdjacentLine;

    Project project = editor.getProject();
    if (project == null) return null;

    DocRenderItemImpl foundItem = null;
    int foundStartOffset = 0;
    for (DocRenderItemImpl item : items) {
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
  @Nullable
  public Collection<DocRenderItem> getItems(@NotNull Editor editor) {
    Collection<DocRenderItemImpl> items = editor.getUserData(OUR_ITEMS);
    if (items == null) return null;
    return Collections.unmodifiableCollection(items);
  }

  @Override
  public void removeAllItems(@NotNull Editor editor) {
    Collection<DocRenderItemImpl> items = editor.getUserData(OUR_ITEMS);
    if (items == null) {
      return;
    }
    keepScrollingPositionWhile(editor, () -> {
      List<Runnable> foldingTasks = new ArrayList<>();
      boolean updated = false;
      for (Iterator<DocRenderItemImpl> it = items.iterator(); it.hasNext(); ) {
        DocRenderItemImpl existingItem = it.next();
        updated |= existingItem.remove(foldingTasks);
        it.remove();
      }
      editor.getFoldingModel().runBatchFoldingOperation(() -> foldingTasks.forEach(Runnable::run), true, false);
      return updated;
    });
    setupListeners(editor, true);
  }

  @Override
  public void setItemsToEditor(@NotNull Editor editor, @NotNull DocRenderPassFactory.Items itemsToSet, boolean collapseNewItems) {
    Collection<DocRenderItemImpl> items;
    Collection<DocRenderItemImpl> existing = editor.getUserData(OUR_ITEMS);
    if (existing == null) {
      if (itemsToSet.isEmpty()) return;
      editor.putUserData(OUR_ITEMS, items = new ArrayList<>());
    }
    else {
      items = existing;
    }
    keepScrollingPositionWhile(editor, () -> {
      List<Runnable> foldingTasks = new ArrayList<>();
      List<DocRenderItemImpl> itemsToUpdateRenderers = new ArrayList<>();
      List<String> itemsToUpdateText = new ArrayList<>();
      boolean updated = false;
      for (Iterator<DocRenderItemImpl> it = items.iterator(); it.hasNext(); ) {
        DocRenderItemImpl existingItem = it.next();
        DocRenderPassFactory.Item matchingNewItem = existingItem.isValid() ? itemsToSet.removeItem(existingItem.getHighlighter()) : null;
        if (matchingNewItem == null) {
          updated |= existingItem.remove(foldingTasks);
          it.remove();
        }
        else if (matchingNewItem.textToRender != null && !matchingNewItem.textToRender.equals(existingItem.getTextToRender())) {
          itemsToUpdateRenderers.add(existingItem);
          itemsToUpdateText.add(matchingNewItem.textToRender);
        }
        else {
          existingItem.updateIcon(foldingTasks);
        }
      }
      Collection<DocRenderItemImpl> newRenderItems = new ArrayList<>();
      for (DocRenderPassFactory.Item item : itemsToSet) {
        DocRenderItemImpl newItem = new DocRenderItemImpl(editor, item.textRange, collapseNewItems ? null : item.textToRender);
        newRenderItems.add(newItem);
        if (collapseNewItems) {
          updated |= newItem.toggle(foldingTasks);
          itemsToUpdateRenderers.add(newItem);
          itemsToUpdateText.add(item.textToRender);
        }
      }
      editor.getFoldingModel().runBatchFoldingOperation(() -> foldingTasks.forEach(Runnable::run), true, false);
      if (!newRenderItems.isEmpty() && collapseNewItems) {
        for (DocRenderItemImpl item : newRenderItems) {
          CustomFoldRegion r = item.getFoldRegion();
          if (r != null) {
            r.update();
          }
        }
      }
      for (int i = 0; i < itemsToUpdateRenderers.size(); i++) {
        itemsToUpdateRenderers.get(i).setTextToRender(itemsToUpdateText.get(i));
      }
      DocRenderItemUpdater.updateRenderers(itemsToUpdateRenderers, true);
      ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).onItemsUpdate(editor, itemsToUpdateRenderers, true);
      items.addAll(newRenderItems);
      return updated;
    });
    setupListeners(editor, items.isEmpty());
  }

  @Override
  public void resetToDefaultState(@NotNull Editor editor) {
    Collection<DocRenderItemImpl> items = editor.getUserData(OUR_ITEMS);
    if (items == null) return;
    boolean editorSetting = DocRenderManager.isDocRenderingEnabled(editor);
    keepScrollingPositionWhile(editor, () -> {
      List<Runnable> foldingTasks = new ArrayList<>();
      boolean updated = false;
      for (DocRenderItemImpl item : items) {
        if (item.isValid() && (item.getFoldRegion() == null) == editorSetting) {
          updated |= item.toggle(foldingTasks);
        }
      }
      editor.getFoldingModel().runBatchFoldingOperation(() -> foldingTasks.forEach(Runnable::run), true, false);
      return updated;
    });
  }

  @Override
  public boolean isRenderedDocHighlighter(@NotNull RangeHighlighter highlighter) {
    return Boolean.TRUE.equals(highlighter.getUserData(OWN_HIGHLIGHTER));
  }
}
