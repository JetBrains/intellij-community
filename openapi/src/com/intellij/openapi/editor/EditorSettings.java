/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;

public interface EditorSettings {
  boolean isRightMarginShown();
  void setRightMarginShown(boolean val);

  boolean isWhitespacesShown();
  void setWhitespacesShown(boolean val);

  int getRightMargin(Project project);
  void setRightMargin(int myRightMargin);

  boolean isLineNumbersShown();
  void setLineNumbersShown(boolean val);

  int getAdditionalLinesCount();
  void setAdditionalLinesCount(int additionalLinesCount);

  int getAdditionalColumnsCount();
  void setAdditionalColumnsCount(int additinalColumnsCount);

  boolean isLineMarkerAreaShown();
  void setLineMarkerAreaShown(boolean lineMarkerAreaShown);

  boolean isFoldingOutlineShown();
  void setFoldingOutlineShown(boolean val);

  boolean isUseTabCharacter(Project project);
  void setUseTabCharacter(boolean useTabCharacter);

  int getTabSize(Project project);
  void setTabSize(int tabSize);

  boolean isSmartHome();
  void setSmartHome(boolean val);

  boolean isVirtualSpace();
  void setVirtualSpace(boolean val);

  boolean isCaretInsideTabs();
  void setCaretInsideTabs(boolean val);

  boolean isBlinkCaret();
  void setBlinkCaret(boolean blinkCaret);

  int getCaretBlinkPeriod();
  void setCaretBlinkPeriod(int blinkPeriod);

  boolean isBlockCursor();
  void setBlockCursor(boolean blockCursor);

  int getLineCursorWidth();
  void setLineCursorWidth(int width);

  boolean isAnimatedScrolling();
  void setAnimatedScrolling(boolean val);

  boolean isCamelWords();
  void setCamelWords(boolean val);

  boolean isAdditionalPageAtBottom();

  void setAdditionalPageAtBottom(boolean val);

  boolean isDndEnabled();
  void setDndEnabled(boolean val);

  boolean isWheelFontChangeEnabled();
  void setWheelFontChangeEnabled(boolean val);

  boolean isMouseClickSelectionHonorsCamelWords();
  void setMouseClickSelectionHonorsCamelWords(boolean val);
}
