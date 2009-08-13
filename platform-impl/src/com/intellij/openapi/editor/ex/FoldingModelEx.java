package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;

/**
 * @author max
 */
public interface FoldingModelEx extends FoldingModel {
  void setFoldingEnabled(boolean isEnabled);
  boolean isFoldingEnabled();

  FoldRegion getFoldingPlaceholderAt(Point p);
  FoldRegion[] getAllFoldRegionsIncludingInvalid();

  boolean intersectsRegion(int startOffset, int endOffset);

  FoldRegion fetchOutermost(int offset);

  int getLastCollapsedRegionBefore(int offset);

  TextAttributes getPlaceholderAttributes();

  FoldRegion[] fetchTopLevel();
}
