// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.util.EditorScrollingPositionKeeper;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;

@Service
public final class DocRenderItemUpdater implements Runnable {
  private static final long MAX_UPDATE_DURATION_MS = 50;
  private final Map<RenderedElement, Boolean> myQueue = new HashMap<>();

  static DocRenderItemUpdater getInstance() {
    return ApplicationManager.getApplication().getService(DocRenderItemUpdater.class);
  }

  void updateFoldRegions(@NotNull Collection<CustomFoldRegion> foldRegions, boolean recreateContent) {
    if (foldRegions.isEmpty()) return;
    boolean wasEmpty = myQueue.isEmpty();
    for (CustomFoldRegion foldRegion : foldRegions) {
      myQueue.merge(new FoldRegionWrapper(foldRegion), recreateContent, Boolean::logicalOr);
    }
    if (wasEmpty) processChunk();
  }

  void updateInlays(@NotNull Collection<Inlay<DocRenderer>> inlays, boolean recreateContent) {
    if (inlays.isEmpty()) return;
    boolean wasEmpty = myQueue.isEmpty();
    for (Inlay<DocRenderer> inlay : inlays) {
      myQueue.merge(new InlayWrapper(inlay), recreateContent, Boolean::logicalOr);
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
    List<RenderedElement> toProcess = new ArrayList<>(myQueue.keySet());
    Object2IntMap<Editor> memoMap = new Object2IntOpenHashMap<>();
    toProcess.sort(Comparator.comparingInt(i -> -Math.abs(i.getOffset() - getVisibleOffset(i.getEditor(), memoMap))));
    do {
      RenderedElement element = toProcess.remove(toProcess.size() - 1);
      boolean updateContent = myQueue.remove(element);
      if (element.isValid()) {
        Editor editor = element.getEditor();
        keepers.computeIfAbsent(editor, e -> {
          EditorScrollingPositionKeeper keeper = new EditorScrollingPositionKeeper(editor);
          keeper.savePosition();
          return keeper;
        });
        element.getRenderer().update(true, updateContent, null);
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

  // remove after migration to new backend
  private interface RenderedElement {
    Editor getEditor();
    int getOffset();
    boolean isValid();
    DocRenderer getRenderer();
  }

  private static class InlayWrapper implements RenderedElement {
    private final Inlay<DocRenderer> myInlay;

    private InlayWrapper(Inlay<DocRenderer> inlay) { myInlay = inlay; }

    @Override
    public Editor getEditor() {
      return myInlay.getEditor();
    }

    @Override
    public int getOffset() {
      return myInlay.getOffset();
    }

    @Override
    public boolean isValid() {
      return myInlay.isValid();
    }

    @Override
    public DocRenderer getRenderer() {
      return myInlay.getRenderer();
    }
  }

  private static class FoldRegionWrapper implements RenderedElement {
    private final CustomFoldRegion myFoldRegion;

    private FoldRegionWrapper(CustomFoldRegion region) { myFoldRegion = region; }

    @Override
    public Editor getEditor() {
      return myFoldRegion.getEditor();
    }

    @Override
    public int getOffset() {
      return myFoldRegion.getStartOffset();
    }

    @Override
    public boolean isValid() {
      return myFoldRegion.isValid();
    }

    @Override
    public DocRenderer getRenderer() {
      return (DocRenderer)myFoldRegion.getRenderer();
    }
  }
}
