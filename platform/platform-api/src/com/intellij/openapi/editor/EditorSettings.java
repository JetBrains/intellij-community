/*
 * Copyright 2000-2009 JetBrains s.r.o.
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


  boolean isVariableInplaceRenameEnabled();
  void setVariableInplaceRenameEnabled(boolean val);

  boolean isRefrainFromScrolling();
  void setRefrainFromScrolling(boolean b);

  boolean isIndentGuidesShown();
  void setIndentGuidesShown(boolean val);

  boolean isUseSoftWraps();
  void setUseSoftWraps(boolean use);

  boolean isAllSoftWrapsShown();
  void setShowAllSoftWraps(boolean show);
}
