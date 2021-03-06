// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

@Service
public final class DocRenderItemUpdater implements Runnable {
  private static final long MAX_UPDATE_DURATION_MS = 50;
  private final Map<Inlay<DocRenderer>, Boolean> myQueue = new HashMap<>();

  static DocRenderItemUpdater getInstance() {
    return ApplicationManager.getApplication().getService(DocRenderItemUpdater.class);
  }

  void updateInlays(@NotNull Collection<Inlay<DocRenderer>> inlays, boolean recreateContent) {
    if (inlays.isEmpty()) return;
    boolean wasEmpty = myQueue.isEmpty();
    for (Inlay<DocRenderer> inlay : inlays) {
      myQueue.merge(inlay, recreateContent, (oldValue, newValue) -> newValue | oldValue);
    }
    if (wasEmpty) processChunk();
  }

  @Override
  public void run() {
    processChunk();
  }

  private void processChunk() {
    long deadline = System.currentTimeMillis() + MAX_UPDATE_DURATION_MS;
    Map<Editor, EditorScrollingPositionKeeper> keepers = new HashMap<>();
    // This is a heuristic to lessen visual 'jumping' on editor opening. We'd like inlays visible at target opening location to be updated
    // first, and all the rest - later. We're not specifically optimizing for the case when multiple editors are opened simultaneously now,
    // opening several editors in succession should work fine with this logic though (by the time a new editor is opened, 'high-priority'
    // inlays from the previous editor are likely to have been processed already).
    List<Inlay<DocRenderer>> toProcess = new ArrayList<>(myQueue.keySet());
    Object2IntMap<Editor> memoMap = new Object2IntOpenHashMap<>();
    toProcess.sort(Comparator.comparingInt(i -> -Math.abs(i.getOffset() - getVisibleOffset(i.getEditor(), memoMap))));
    do {
      Inlay<DocRenderer> inlay = toProcess.remove(toProcess.size() - 1);
      boolean updateContent = myQueue.remove(inlay);
      if (inlay.isValid()) {
        Editor editor = inlay.getEditor();
        keepers.computeIfAbsent(editor, e -> {
          EditorScrollingPositionKeeper keeper = new EditorScrollingPositionKeeper(editor);
          keeper.savePosition();
          return keeper;
        });
        inlay.getRenderer().update(true, updateContent);
      }
    }
    while (!toProcess.isEmpty() && System.currentTimeMillis() < deadline);
    keepers.values().forEach(k -> k.restorePosition(false));
    if (!myQueue.isEmpty()) SwingUtilities.invokeLater(this);
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
