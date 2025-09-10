// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RangeHighlighterTree extends RangeMarkerTree<RangeHighlighterEx> {
  static final byte ERROR_STRIPE_FLAG = nextAvailableTasteFlag();
  static final byte RENDER_IN_GUTTER_FLAG = nextAvailableTasteFlag();
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

  @Override
  protected byte getTasteFlags(@NotNull RangeHighlighterEx highlighter) {
    return (byte)((highlighter.getErrorStripeMarkColor(null) != null ? ERROR_STRIPE_FLAG : 0) |
                 (highlighter.isRenderedInGutter() ? RENDER_IN_GUTTER_FLAG : 0));
  }

  @Override
  protected int compareEqualStartIntervals(@NotNull IntervalNode<RangeHighlighterEx> i1, @NotNull IntervalNode<RangeHighlighterEx> i2) {
    return ((RHNode)i1).compareTo((RHNode)i2);
  }

  @Override
  protected @NotNull RMNode<RangeHighlighterEx> createNewNode(@NotNull RangeHighlighterEx key, int start, int end,
                                                              boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new RHNode(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
  }

  @ApiStatus.Internal
  static final class RHNode extends RMNode<RangeHighlighterEx> implements Comparable<RMNode<?>> {
    private static final byte IS_PERSISTENT = (byte)(STICK_TO_RIGHT_FLAG << 1);
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

    @Override
    public int compareTo(@NotNull RMNode o) {
      RHNode o1 = this;
      RHNode o2 = (RHNode)o;
      int d = o2.myLayer - o1.myLayer;
      if (d != 0) {
        return d;
      }
      int result = super.compareTo(o2);
      if (result != 0) {
        return result;
      }

      boolean persistent1 = o1.isFlagSet(RHNode.IS_PERSISTENT);
      boolean persistent2 = o2.isFlagSet(RHNode.IS_PERSISTENT);
      return persistent1 == persistent2 ? 0 : persistent1 ? -1 : 1;
    }
  }

  @Override
  protected void fireBeforeRemoved(@NotNull RangeHighlighterEx marker) {
    myMarkupModel.fireBeforeRemoved(marker);
  }

  @Override
  protected void fireAfterRemoved(@NotNull RangeHighlighterEx marker) {
    myMarkupModel.fireAfterRemoved(marker);
  }
}
