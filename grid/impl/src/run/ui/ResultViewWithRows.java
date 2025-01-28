package com.intellij.database.run.ui;

import com.intellij.database.run.ui.grid.CacheComponent;

/**
 * @author Liudmila Kornilova
 **/
public interface ResultViewWithRows extends CacheComponent {
  void resetRowHeights();
  int getRowHeight();
}
