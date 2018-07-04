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
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface EditorSettings {
  boolean isRightMarginShown();
  void setRightMarginShown(boolean val);

  boolean isWhitespacesShown();
  void setWhitespacesShown(boolean val);

  boolean isLeadingWhitespaceShown();
  void setLeadingWhitespaceShown(boolean val);

  boolean isInnerWhitespaceShown();
  void setInnerWhitespaceShown(boolean val);

  boolean isTrailingWhitespaceShown();
  void setTrailingWhitespaceShown(boolean val);

  int getRightMargin(Project project);
  void setRightMargin(int myRightMargin);

  /**
   * Retrieves a list of soft margins (visual indent guides) to be used in the editor. If soft margins haven't been explicitly set
   * with {@link #setSoftMargins(List)} method, they are obtained from code style settings: {@code CodeStyleSettings.getSoftMargins()}.
   * @return A list of current editor soft margins. The list may be empty if no soft margins are defined.
   */
  @NotNull
  List<Integer> getSoftMargins();

  /**
   * Explicitly sets soft margins (visual indent guides) to be used in the editor instead of obtaining them from code style settings via
   * {@code CodeStyleSettings.getSoftMargins()} method. It is important to distinguish and empty list from {@code null} value: the first
   * will define no soft margins for the eidtor while the latter will restore the default behavior of using them from code style settings.
   * @param softMargins A list of soft margins or {@code null} to use margins from code style settings.
   */
  void setSoftMargins(@Nullable List<Integer> softMargins);

  boolean isWrapWhenTypingReachesRightMargin(Project project);
  void setWrapWhenTypingReachesRightMargin(boolean val);

  boolean isLineNumbersShown();
  void setLineNumbersShown(boolean val);

  int getAdditionalLinesCount();
  void setAdditionalLinesCount(int additionalLinesCount);

  int getAdditionalColumnsCount();
  void setAdditionalColumnsCount(int additionalColumnsCount);

  boolean isLineMarkerAreaShown();
  void setLineMarkerAreaShown(boolean lineMarkerAreaShown);

  boolean areGutterIconsShown();
  void setGutterIconsShown(boolean gutterIconsShown);

  boolean isFoldingOutlineShown();
  void setFoldingOutlineShown(boolean val);

  boolean isAutoCodeFoldingEnabled();
  void setAutoCodeFoldingEnabled(boolean val);

  boolean isUseTabCharacter(Project project);
  void setUseTabCharacter(boolean useTabCharacter);

  int getTabSize(Project project);
  void setTabSize(int tabSize);

  boolean isSmartHome();
  void setSmartHome(boolean val);

  boolean isVirtualSpace();
  void setVirtualSpace(boolean allow);

  boolean isCaretInsideTabs();
  void setCaretInsideTabs(boolean allow);

  boolean isBlinkCaret();
  void setBlinkCaret(boolean blinkCaret);

  int getCaretBlinkPeriod();
  void setCaretBlinkPeriod(int blinkPeriod);

  boolean isBlockCursor();
  void setBlockCursor(boolean blockCursor);

  boolean isCaretRowShown();
  void setCaretRowShown(boolean caretRowShown);

  int getLineCursorWidth();
  void setLineCursorWidth(int width);

  boolean isAnimatedScrolling();
  void setAnimatedScrolling(boolean val);

  boolean isCamelWords();
  void setCamelWords(boolean val);
  /** Allows to remove 'use camel words' setup specific to the current settings object (if any) and use the shared one. */
  void resetCamelWords();

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
  boolean isUseCustomSoftWrapIndent();
  void setUseCustomSoftWrapIndent(boolean useCustomSoftWrapIndent);
  int getCustomSoftWrapIndent();
  void setCustomSoftWrapIndent(int indent);

  boolean isAllowSingleLogicalLineFolding();
  void setAllowSingleLogicalLineFolding(boolean allow);

  boolean isPreselectRename();
  void setPreselectRename(final boolean val);

  boolean isShowIntentionBulb();
  void setShowIntentionBulb(boolean show);
}
