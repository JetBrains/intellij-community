// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.List;

@ApiStatus.Experimental
public interface EditorSettingsListener extends EventListener {
  default void isRightMarginShownChanged(boolean newValue) {}
  default void isWhitespacesShownChanged(boolean newValue) {}
  default void isLeadingWhitespaceShownChanged(boolean newValue) {}
  default void isInnerWhitespaceShownChanged(boolean newValue) {}
  default void isTrailingWhitespaceShownChanged(boolean newValue) {}
  default void isSelectionWhitespaceShownChanged(boolean newValue) {}
  default void rightMarginChanged(int newValue) {}
  default void softMarginsChanged(@NotNull List<Integer> newValue) {}
  default void isWrapWhenTypingReachesRightMarginChanged(boolean newValue) {}
  default void isLineNumbersShownChanged(boolean newValue) {}
  default void additionalLinesCountChanged(int newValue) {}
  default void additionalColumnsCountChanged(int newValue) {}
  default void isLineMarkerAreaShownChanged(boolean newValue) {}
  default void areGutterIconsShownChanged(boolean newValue) {}
  default void isFoldingOutlineShownChanged(boolean newValue) {}
  default void isAutoCodeFoldingEnabledChanged(boolean newValue) {}
  default void isUseTabCharacterChanged(boolean newValue) {}
  default void tabSizeChanged(int newValue) {}
  default void isSmartHomeChanged(boolean newValue) {}
  default void isVirtualSpaceChanged(boolean newValue) {}
  default void verticalScrollOffsetChanged(int newValue) {}
  default void verticalScrollJumpChanged(int newValue) {}
  default void horizontalScrollOffsetChanged(int newValue) {}
  default void horizontalScrollJumpChanged(int newValue) {}
  default void isCaretInsideTabsChanged(boolean newValue) {}
  default void isBlinkCaretChanged(boolean newValue) {}
  default void caretBlinkPeriodChanged(int newValue) {}
  default void isBlockCursorChanged(boolean newValue) {}
  default void isCaretRowShownChanged(boolean newValue) {}
  default void lineCursorWidthChanged(int newValue) {}
  default void isAnimatedScrollingChanged(boolean newValue) {}
  default void isCamelWordsChanged(boolean newValue) {}
  default void isAdditionalPageAtBottomChanged(boolean newValue) {}
  default void isDndEnabledChanged(boolean newValue) {}
  default void isWheelFontChangeEnabledChanged(boolean newValue) {}
  default void isMouseClickSelectionHonorsCamelWordsChanged(boolean newValue) {}
  default void isVariableInplaceRenameEnabledChanged(boolean newValue) {}
  default void isRefrainFromScrollingChanged(boolean newValue) {}
  default void isIndentGuidesShownChanged(boolean newValue) {}
  default void isUseSoftWrapsChanged(boolean newValue) {}
  default void isPaintSoftWrapsChanged(boolean newValue) {}
  default void isUseCustomSoftWrapIndentChanged(boolean newValue) {}
  default void customSoftWrapIndentChanged(int newValue) {}
  default void isAllowSingleLogicalLineFoldingChanged(boolean newValue) {}
  default void isPreselectRenameChanged(boolean newValue) {}
  default void isShowIntentionBulbChanged(boolean newValue) {}
  default void isShowingSpecialCharsChanged(boolean newValue) {}
  default void lineNumerationTypeChanged(@NotNull EditorSettings.LineNumerationType newValue) {}
}
