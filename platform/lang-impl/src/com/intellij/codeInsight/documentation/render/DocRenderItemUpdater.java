// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

@Service
public final class DocRenderItemUpdater implements Runnable {
  private static final long MAX_UPDATE_DURATION_MS = 50;
  private final Map<CustomFoldRegion, Boolean> myQueue = new HashMap<>();

  static DocRenderItemUpdater getInstance() {
    return ApplicationManager.getApplication().getService(DocRenderItemUpdater.class);
  }

  public static void updateRenderers(@NotNull Collection<? extends DocRenderItem> items, boolean recreateContent, Runnable onAfterDone) {
    getInstance().updateFoldRegions(ContainerUtil.mapNotNull(items, i -> i.getFoldRegion()), recreateContent, onAfterDone);
  }

  public static void updateRenderers(@NotNull Collection<? extends DocRenderItem> items, boolean recreateContent) {
    getInstance().updateFoldRegions(ContainerUtil.mapNotNull(items, i -> i.getFoldRegion()), recreateContent);
  }

  static void updateRenderers(@NotNull Editor editor, boolean recreateContent) {
    if (recreateContent) {
      DocRenderer.clearCachedLoadingPane(editor);
    }
    Collection<? extends DocRenderItem> items = DocRenderItemManager.getInstance().getItems(editor);
    if (items != null) updateRenderers(items, recreateContent);
  }

  void updateFoldRegions(@NotNull Collection<? extends  CustomFoldRegion> foldRegions, boolean recreateContent, Runnable onAfterDone) {
    if (foldRegions.isEmpty()) return;
    boolean wasEmpty = myQueue.isEmpty();
    for (CustomFoldRegion foldRegion : foldRegions) {
      myQueue.merge(foldRegion, recreateContent, Boolean::logicalOr);
    }
    if (wasEmpty) processChunk(onAfterDone);
  }

  void updateFoldRegions(@NotNull Collection<? extends CustomFoldRegion> foldRegions, boolean recreateContent) {
    if (foldRegions.isEmpty()) return;
    boolean wasEmpty = myQueue.isEmpty();
    for (CustomFoldRegion foldRegion : foldRegions) {
      myQueue.merge(foldRegion, recreateContent, Boolean::logicalOr);
    }
    if (wasEmpty) processChunk(null);
  }

  @Override
  public void run() {
    processChunk(null);
  }

  private void processChunk(@Nullable Runnable onAfterDone) {
    long deadline = System.currentTimeMillis() + MAX_UPDATE_DURATION_MS;
    Map<Editor, EditorScrollingPositionKeeper> keepers = new HashMap<>();
    // This is a heuristic to lessen visual 'jumping' on editor opening. We'd like regions visible at target opening location to be updated
    // first, and all the rest - later. We're not specifically optimizing for the case when multiple editors are opened simultaneously now,
    // opening several editors in succession should work fine with this logic though (by the time a new editor is opened, 'high-priority'
    // regions from the previous editor are likely to have been processed already).
    List<CustomFoldRegion> toProcess = new ArrayList<>(myQueue.keySet());
    Object2IntMap<Editor> memoMap = new Object2IntOpenHashMap<>();
    toProcess.sort(Comparator.comparingInt(i -> -Math.abs(i.getStartOffset() - getVisibleOffset(i.getEditor(), memoMap))));
    Map<Editor, List<Runnable>> editorTasks = new HashMap<>();
    do {
      CustomFoldRegion region = toProcess.remove(toProcess.size() - 1);
      boolean updateContent = myQueue.remove(region);
      if (region.isValid()) {
        Editor editor = region.getEditor();
        keepers.computeIfAbsent(editor, e -> {
          EditorScrollingPositionKeeper keeper = new EditorScrollingPositionKeeper(editor);
          keeper.savePosition();
          return keeper;
        });
        var tasks = editorTasks.computeIfAbsent(editor, e -> new ArrayList<>());
        ((DocRenderer)region.getRenderer()).update(true, updateContent, tasks);
        if (tasks.size() > 20) {
          runFoldingTasks(editor, tasks);
        }
      }
    }
    while (!toProcess.isEmpty() && System.currentTimeMillis() < deadline);
    editorTasks.entrySet().forEach((entry -> runFoldingTasks(entry.getKey(), entry.getValue())));
    keepers.values().forEach(k -> k.restorePosition(false));
    if (!myQueue.isEmpty()) ApplicationManager.getApplication().invokeLater(() -> processChunk(onAfterDone));
    if (myQueue.isEmpty() && onAfterDone != null) onAfterDone.run();
  }

  private static void runFoldingTasks(@NotNull Editor editor, @NotNull List<Runnable> tasks) {
    editor.getFoldingModel().runBatchFoldingOperation(() -> tasks.forEach(Runnable::run), true, false);
    tasks.clear();
  }

  private static int getVisibleOffset(Editor editor, Object2IntMap<Editor> memoMap) {
    return memoMap.computeIntIfAbsent(editor, e -> {
      Rectangle visibleArea = e.getScrollingModel().getVisibleAreaOnScrollingFinished();
      if (editor.isDisposed() || visibleArea.height <= 0) {
        return e.getCaretModel().getOffset();
      }
      else {
        int y = visibleArea.y + visibleArea.height / 2;
        int visualLine = e.yToVisualLine(y);
        return e.visualPositionToOffset(new VisualPosition(visualLine, 0));
      }
    });
  }
}
