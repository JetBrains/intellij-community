package com.intellij.ide.todo;

import com.intellij.ui.HighlightedRegion;

/**
 * @author Vladimir Kondratyev
 */
public interface HighlightedRegionProvider{
  Iterable<HighlightedRegion> getHighlightedRegions();
}
