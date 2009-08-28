package com.intellij.execution.ui.layout;

import com.intellij.ui.content.Content;

import java.util.List;

public interface Grid {
  GridCell getCellFor(final Content content);

  List<Content> getContents();
}