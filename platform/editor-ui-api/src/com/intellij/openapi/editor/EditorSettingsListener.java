// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

@ApiStatus.Experimental
public interface EditorSettingsListener extends EventListener {
  void isRightMarginShownChanged(boolean newValue);
  void isWhitespacesShownChanged(boolean newValue);
  void isLeadingWhitespaceShownChanged(boolean newValue);
  void isInnerWhitespaceShownChanged(boolean newValue);
  void isTrailingWhitespaceShownChanged(boolean newValue);
  void isSelectionWhitespaceShownChanged(boolean newValue);
  void rightMarginChanged(int newValue);
  void softMarginsChanged(@NotNull List<Integer> newValue);
  void isWrapWhenTypingReachesRightMarginChanged(boolean newValue);
  void isLineNumbersShownChanged(boolean newValue);
  void additionalLinesCountChanged(int newValue);
  void additionalColumnsCountChanged(int newValue);
  void isLineMarkerAreaShownChanged(boolean newValue);
  void areGutterIconsShownChanged(boolean newValue);
  void isFoldingOutlineShownChanged(boolean newValue);
  void isAutoCodeFoldingEnabledChanged(boolean newValue);
  void isUseTabCharacterChanged(boolean newValue);
  void tabSizeChanged(int newValue);
  void isSmartHomeChanged(boolean newValue);
  void isVirtualSpaceChanged(boolean newValue);
  void verticalScrollOffsetChanged(int newValue);
  void verticalScrollJumpChanged(int newValue);
  void horizontalScrollOffsetChanged(int newValue);
  void horizontalScrollJumpChanged(int newValue);
  void isCaretInsideTabsChanged(boolean newValue);
  void isBlinkCaretChanged(boolean newValue);
  void caretBlinkPeriodChanged(int newValue);
  void isBlockCursorChanged(boolean newValue);
  void isCaretRowShownChanged(boolean newValue);
  void lineCursorWidthChanged(int newValue);
  void isAnimatedScrollingChanged(boolean newValue);
  void isCamelWordsChanged(boolean newValue);
  void isAdditionalPageAtBottomChanged(boolean newValue);
  void isDndEnabledChanged(boolean newValue);
  void isWheelFontChangeEnabledChanged(boolean newValue);
  void isMouseClickSelectionHonorsCamelWordsChanged(boolean newValue);
  void isVariableInplaceRenameEnabledChanged(boolean newValue);
  void isRefrainFromScrollingChanged(boolean newValue);
  void isIndentGuidesShownChanged(boolean newValue);
  void isUseSoftWrapsChanged(boolean newValue);
  void isPaintSoftWrapsChanged(boolean newValue);
  void isUseCustomSoftWrapIndentChanged(boolean newValue);
  void customSoftWrapIndentChanged(int newValue);
  void isAllowSingleLogicalLineFoldingChanged(boolean newValue);
  void isPreselectRenameChanged(boolean newValue);
  void isShowIntentionBulbChanged(boolean newValue);
  void isShowingSpecialCharsChanged(boolean newValue);
  void lineNumerationTypeChanged(@NotNull EditorSettings.LineNumerationType newValue);
}
