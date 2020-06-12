/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.MarkupIterator;
import com.intellij.openapi.editor.ex.MarkupModelEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.util.Getter;
import org.jetbrains.annotations.NotNull;

class RangeHighlighterTree extends RangeMarkerTree<RangeHighlighterEx> {
  private final MarkupModelEx myMarkupModel;

  RangeHighlighterTree(@NotNull Document document, @NotNull MarkupModelEx markupModel) {
    super(document);
    myMarkupModel = markupModel;
  }

  @Override
  protected boolean keepIntervalsOnWeakReferences() {
    return false;
  }

  @NotNull
  MarkupIterator<RangeHighlighterEx> overlappingIterator(@NotNull TextRangeInterval rangeInterval, boolean onlyRenderedInGutter) {
    MarkupIterator<RangeHighlighterEx> iterator =
      overlappingIterator(rangeInterval, node -> (!onlyRenderedInGutter || node.isFlagSet(RHNode.RENDERED_IN_GUTTER_FLAG)));

    return new FilteringMarkupIterator<>(iterator, highlighter -> !onlyRenderedInGutter || highlighter.isRenderedInGutter());
  }

  void updateRenderedFlags(RangeHighlighterEx highlighter) {
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
    return super.compareEqualStartIntervals(i1, i2);
  }

  @NotNull
  @Override
  protected RHNode createNewNode(@NotNull RangeHighlighterEx key, int start, int end, 
                                 boolean greedyToLeft, boolean greedyToRight, boolean stickingToRight, int layer) {
    return new RHNode(this, key, start, end, greedyToLeft, greedyToRight, stickingToRight, layer);
  }

  static class RHNode extends RMNode<RangeHighlighterEx> {
    private static final byte RENDERED_IN_GUTTER_FLAG = STICK_TO_RIGHT_FLAG << 1;

    final int myLayer;

    RHNode(@NotNull RangeHighlighterTree rangeMarkerTree,
           @NotNull final RangeHighlighterEx key,
           int start,
           int end,
           boolean greedyToLeft,
           boolean greedyToRight,
           boolean stickingToRight,
           int layer) {
      super(rangeMarkerTree, key, start, end, greedyToLeft, greedyToRight, stickingToRight);
      myLayer = layer;
    }

    private void recalculateRenderFlags() {
      boolean renderedInGutter = false;
      for (Getter<RangeHighlighterEx> getter : intervals) {
        RangeHighlighterEx h = getter.get();
        renderedInGutter |= h.isRenderedInGutter();
      }
      Node<RangeHighlighterEx> left = getLeft();
      if (left != null) {
        renderedInGutter |= left.isFlagSet(RENDERED_IN_GUTTER_FLAG);
      }
      Node<RangeHighlighterEx> right = getRight();
      if (right != null) {
        renderedInGutter |= right.isFlagSet(RENDERED_IN_GUTTER_FLAG);
      }
      setFlag(RENDERED_IN_GUTTER_FLAG, renderedInGutter);
    }

    private void recalculateRenderFlagsUp() {
      RHNode n = this;
      while (n != null) {
        boolean prevInGutter = n.isFlagSet(RENDERED_IN_GUTTER_FLAG);
        n.recalculateRenderFlags();
        if (n.isFlagSet(RENDERED_IN_GUTTER_FLAG) == prevInGutter) break;
        n = (RHNode)n.getParent();
      }
    }

    @Override
    void addInterval(@NotNull RangeHighlighterEx h) {
      super.addInterval(h);
      if (!isFlagSet(RENDERED_IN_GUTTER_FLAG) && h.isRenderedInGutter()) {
        recalculateRenderFlagsUp();
      }
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
  void fireBeforeRemoved(@NotNull RangeHighlighterEx markerEx, @NotNull Object reason) {
    myMarkupModel.fireBeforeRemoved(markerEx);
  }
}
