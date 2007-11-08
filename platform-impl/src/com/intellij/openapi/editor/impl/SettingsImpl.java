/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 19, 2002
 * Time: 3:19:05 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.Nullable;

public class SettingsImpl implements EditorSettings {
  @Nullable private final EditorEx myEditor;
  private Boolean myIsCamelWords;

  public SettingsImpl(@Nullable EditorEx editor) {
    myEditor = editor;
  }

  // This group of settings does not have UI
  private int myAdditionalLinesCount = 5;
  private int myAdditionalColumnsCount = 3;
  private int myLineCursorWidth = 2;
  private boolean myLineMarkerAreaShown = true;

  // These comes from CodeStyleSettings
  private Integer myTabSize = null;
  private Integer myCachedTabSize;
  private Boolean myUseTabCharacter = null;

  // These comes from EditorSettingsExternalizable defaults.
  private Boolean myIsVirtualSpace = null;
  private Boolean myIsCaretInsideTabs = null;
  private Boolean myIsCaretBlinking = null;
  private Integer myCaretBlinkingPeriod = null;
  private Boolean myIsRightMarginShown = null;
  private Integer myRightMargin = null;
  private Boolean myAreLineNumbersShown = null;
  private Boolean myIsFoldingOutlineShown = null;
  private Boolean myIsSmartHome = null;
  private Boolean myIsBlockCursor = null;
  private Boolean myIsWhitespacesShown = null;
  private Boolean myIsAnimatedScrolling = null;
  private Boolean myIsAdditionalPageAtBottom = null;
  private Boolean myIsDndEnabled = null;
  private Boolean myIsWheelFontChangeEnabled = null;
  private Boolean myIsMouseClickSelectionHonorsCamelWords = null;
  private Boolean myIsRenameVariablesInplace = null;
  private Boolean myIsRefrainFromScrolling = null;

  public boolean isRightMarginShown() {
    return myIsRightMarginShown != null
           ? myIsRightMarginShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isRightMarginShown();
  }

  public void setRightMarginShown(boolean val) {
    myIsRightMarginShown = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public boolean isWhitespacesShown() {
    return myIsWhitespacesShown != null
           ? myIsWhitespacesShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWhitespacesShown();
  }

  public void setWhitespacesShown(boolean val) {
    myIsWhitespacesShown = Boolean.valueOf(val);
  }

  public boolean isLineNumbersShown() {
    return myAreLineNumbersShown != null
           ? myAreLineNumbersShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isLineNumbersShown();
  }

  public void setLineNumbersShown(boolean val) {
    myAreLineNumbersShown = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public int getRightMargin(Project project) {
    return myRightMargin != null ? myRightMargin.intValue() :
           CodeStyleSettingsManager.getSettings(project).RIGHT_MARGIN;
  }

  public void setRightMargin(int rightMargin) {
    myRightMargin = new Integer(rightMargin);
    fireEditorRefresh();
  }

  public int getAdditionalLinesCount() {
    return myAdditionalLinesCount;
  }

  public void setAdditionalLinesCount(int additionalLinesCount) {
    myAdditionalLinesCount = additionalLinesCount;
    fireEditorRefresh();
  }

  public int getAdditionalColumnsCount() {
    return myAdditionalColumnsCount;
  }

  public void setAdditionalColumnsCount(int additinalColumnsCount) {
    myAdditionalColumnsCount = additinalColumnsCount;
    fireEditorRefresh();
  }

  public boolean isLineMarkerAreaShown() {
    return myLineMarkerAreaShown;
  }

  public void setLineMarkerAreaShown(boolean lineMarkerAreaShown) {
    myLineMarkerAreaShown = lineMarkerAreaShown;
    fireEditorRefresh();
  }

  public boolean isFoldingOutlineShown() {
    return myIsFoldingOutlineShown != null
           ? myIsFoldingOutlineShown.booleanValue()
           : EditorSettingsExternalizable.getInstance().isFoldingOutlineShown();
  }

  public void setFoldingOutlineShown(boolean val) {
    myIsFoldingOutlineShown = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public boolean isUseTabCharacter(Project project) {
    FileType fileType = getFileType();
    return myUseTabCharacter != null ? myUseTabCharacter.booleanValue() : CodeStyleSettingsManager.getSettings(project)
      .useTabCharacter(fileType);
  }

  public void setUseTabCharacter(boolean val) {
    myUseTabCharacter = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public void reinitSettings() {
    myCachedTabSize = null;
  }

  public int getTabSize(Project project) {
    if (myTabSize != null) return myTabSize.intValue();
    if (myCachedTabSize != null) return myCachedTabSize.intValue();

    FileType fileType = getFileType();
    int tabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(fileType);
    myCachedTabSize = new Integer(tabSize);
    return tabSize;
  }

  @Nullable
  private FileType getFileType() {
    VirtualFile file = myEditor == null ? null : myEditor.getVirtualFile();
    return file == null ? null : file.getFileType();
  }

  public void setTabSize(int tabSize) {
    myTabSize = new Integer(tabSize);
    fireEditorRefresh();
  }

  public boolean isSmartHome() {
    return myIsSmartHome != null
           ? myIsSmartHome.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmartHome();
  }

  public void setSmartHome(boolean val) {
    myIsSmartHome = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public boolean isVirtualSpace() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsVirtualSpace != null
           ? myIsVirtualSpace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVirtualSpace();
  }

  public void setVirtualSpace(boolean val) {
    myIsVirtualSpace = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public boolean isAdditionalPageAtBottom() {
    return myIsAdditionalPageAtBottom != null
           ? myIsAdditionalPageAtBottom.booleanValue()
           : EditorSettingsExternalizable.getInstance().isAdditionalPageAtBottom();
  }

  public void setAdditionalPageAtBottom(boolean val) {
    myIsAdditionalPageAtBottom = new Boolean(val);
  }

  public boolean isCaretInsideTabs() {
    if (myEditor != null && myEditor.isColumnMode()) return true;
    return myIsCaretInsideTabs != null
           ? myIsCaretInsideTabs.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCaretInsideTabs();
  }

  public void setCaretInsideTabs(boolean val) {
    myIsCaretInsideTabs = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public boolean isBlockCursor() {
    return myIsBlockCursor != null
           ? myIsBlockCursor.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlockCursor();
  }

  public void setBlockCursor(boolean val) {
    myIsBlockCursor = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public int getLineCursorWidth() {
    return myLineCursorWidth;
  }

  public void setLineCursorWidth(int width) {
    myLineCursorWidth = width;
  }

  public boolean isAnimatedScrolling() {
    return myIsAnimatedScrolling != null
           ? myIsAnimatedScrolling.booleanValue()
           : EditorSettingsExternalizable.getInstance().isSmoothScrolling();
  }

  public void setAnimatedScrolling(boolean val) {
    myIsAnimatedScrolling = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isCamelWords() {
    return myIsCamelWords != null
           ? myIsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isCamelWords();
  }

  public void setCamelWords(boolean val) {
    myIsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isBlinkCaret() {
    return myIsCaretBlinking != null
           ? myIsCaretBlinking.booleanValue()
           : EditorSettingsExternalizable.getInstance().isBlinkCaret();
  }

  public void setBlinkCaret(boolean val) {
    myIsCaretBlinking = val ? Boolean.TRUE : Boolean.FALSE;
    fireEditorRefresh();
  }

  public int getCaretBlinkPeriod() {
    return myCaretBlinkingPeriod != null
           ? myCaretBlinkingPeriod.intValue()
           : EditorSettingsExternalizable.getInstance().getBlinkPeriod();
  }

  public void setCaretBlinkPeriod(int blinkPeriod) {
    myCaretBlinkingPeriod = new Integer(blinkPeriod);
    fireEditorRefresh();
  }

  public boolean isDndEnabled() {
    return myIsDndEnabled != null ? myIsDndEnabled.booleanValue() : EditorSettingsExternalizable.getInstance().isDndEnabled();
  }

  public void setDndEnabled(boolean val) {
    myIsDndEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isWheelFontChangeEnabled() {
    return myIsWheelFontChangeEnabled != null
           ? myIsWheelFontChangeEnabled.booleanValue()
           : EditorSettingsExternalizable.getInstance().isWheelFontChangeEnabled();
  }

  public void setWheelFontChangeEnabled(boolean val) {
    myIsWheelFontChangeEnabled = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isMouseClickSelectionHonorsCamelWords() {
    return myIsMouseClickSelectionHonorsCamelWords != null
           ? myIsMouseClickSelectionHonorsCamelWords.booleanValue()
           : EditorSettingsExternalizable.getInstance().isMouseClickSelectionHonorsCamelWords();
  }

  public void setMouseClickSelectionHonorsCamelWords(boolean val) {
    myIsMouseClickSelectionHonorsCamelWords = val ? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isVariableInplaceRenameEnabled() {
    return myIsRenameVariablesInplace != null
           ? myIsRenameVariablesInplace.booleanValue()
           : EditorSettingsExternalizable.getInstance().isVariableInplaceRenameEnabled();
  }

  public void setVariableInplaceRenameEnabled(boolean val) {
    myIsRenameVariablesInplace = val? Boolean.TRUE : Boolean.FALSE;
  }

  public boolean isRefrainFromScrolling() {
    if (myIsRefrainFromScrolling != null) return myIsRefrainFromScrolling.booleanValue();
    return EditorSettingsExternalizable.getInstance().isRefrainFromScrolling();
  }


  public void setRefrainFromScrolling(boolean b) {
    myIsRefrainFromScrolling = b ? Boolean.TRUE : Boolean.FALSE;
  }

  private void fireEditorRefresh() {
    if (myEditor != null) {
      myEditor.reinitSettings();
    }
  }
}
