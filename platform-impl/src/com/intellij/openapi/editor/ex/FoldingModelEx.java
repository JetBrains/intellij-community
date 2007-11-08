package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;

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
}
