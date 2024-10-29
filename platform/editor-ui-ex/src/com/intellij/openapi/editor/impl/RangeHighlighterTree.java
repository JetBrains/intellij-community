// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

final class RangeHighlighterTree extends RangeMarkerTree<RangeHighlighterEx> {
  private final MarkupModelImpl myMarkupModel;

  RangeHighlighterTree(@NotNull MarkupModelImpl markupModel) {
    super(markupModel.getDocument());
    myMarkupModel = markupModel;
  }

  void dispose() {
    dispose(myMarkupModel.getDocument());
  }

  @Override
  protected boolean keepIntervalOnWeakReference(@NotNull RangeHighlighterEx interval) {
    return false;
  }

  void updateRenderedFlags(@NotNull RangeHighlighterEx highlighter) {
    RHNode node = (RHNode)lookupNode(highlighter);
    if (node != null) node.recalculateRenderFlagsUp();
  }

  @Override
  void correctMax(@NotNull IntervalNode<RangeHighlighterEx> node, int deltaUpToRoot) {
    super.correctMax(node, deltaUpToRoot);
    ((RHNode)node).recalculateRenderFlags();
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalNode<RangeHighlighterEx> i1, @NotNull IntervalNode<RangeHighlighterEx> i2) {
    RHNode o1 = (RHNode)i1;
    RHNode o2 = (RHNode)i2;
    int d = o2.myLayer - o1.myLayer;
    if (d != 0) {
      return d;
    }
    int result = super.compareEqualStartIntervals(i1, i2);
    if (result != 0) {
      return result;
    }

    boolean persistent1 = o1.isFlagSet(RHNode.IS_PERSISTENT);
    boolean persistent2 = o2.isFlagSet(RHNode.IS_PERSISTENT);
    return persistent1 == persistent2 ? 0 : persistent1 ? -1 : 1;
  }

  @Override
  protected @NotNull RHNode createNewNode(@NotNull RangeHighlighterEx key, int start, int end,
                                          boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new RHNode(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
  }

  static class RHNode extends RMNode<RangeHighlighterEx> {
    private static final byte RENDERED_IN_GUTTER_FLAG = STICK_TO_RIGHT_FLAG << 1;
    static final byte IS_PERSISTENT = (byte)(RENDERED_IN_GUTTER_FLAG << 1);

    final int myLayer;

    RHNode(@NotNull RangeHighlighterTree rangeMarkerTree,
           final @NotNull RangeHighlighterEx key,
           int start,
           int end,
           boolean greedyToLeft,
           boolean greedyToRight,
           boolean stickingToRight,
           int layer) {
      super(rangeMarkerTree, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
      myLayer = layer;
      setFlag(IS_PERSISTENT, key.isPersistent());
    }

    private void recalculateRenderFlags() {
      boolean renderedInGutter = false;
      for (Supplier<? extends RangeHighlighterEx> getter : intervals) {
        RangeHighlighterEx h = getter.get();
        renderedInGutter |= h.isRenderedInGutter();
      }
      RHNode left = (RHNode)getLeft();
      if (left != null) {
        renderedInGutter |= left.isRenderedInGutter();
      }
      RHNode right = (RHNode)getRight();
      if (right != null) {
        renderedInGutter |= right.isRenderedInGutter();
      }
      setFlag(RENDERED_IN_GUTTER_FLAG, renderedInGutter);
    }

    private void recalculateRenderFlagsUp() {
      getTree().l.writeLock().lock();
      try {
        RHNode n = this;
        while (n != null) {
          boolean prevInGutter = n.isRenderedInGutter();
          n.recalculateRenderFlags();
          if (n.isRenderedInGutter() == prevInGutter) break;
          n = (RHNode)n.getParent();
        }
      }
      finally {
        getTree().l.writeLock().unlock();
      }
    }

    @Override
    void addInterval(@NotNull RangeHighlighterEx h) {
      super.addInterval(h);
      if (!isRenderedInGutter() && h.isRenderedInGutter()) {
        recalculateRenderFlagsUp();
      }
    }

    boolean isRenderedInGutter() {
      return isFlagSet(RENDERED_IN_GUTTER_FLAG);
    }

    @Override
    void removeIntervalInternal(int i) {
      RangeHighlighterEx h = intervals.get(i).get();
      boolean recalculateFlags = h.isRenderedInGutter();
      super.removeIntervalInternal(i);
      if (recalculateFlags) recalculateRenderFlagsUp();
    }
  }

  @Override
  void fireBeforeRemoved(@NotNull RangeHighlighterEx marker) {
    myMarkupModel.fireBeforeRemoved(marker);
  }

  @Override
  void fireAfterRemoved(@NotNull RangeHighlighterEx marker) {
    myMarkupModel.fireAfterRemoved(marker);
  }
}
