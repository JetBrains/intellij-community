/*
 * @author max
 */
package com.intellij.codeStyle;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultCodeStyleFacade extends CodeStyleFacade {
  public int getIndentSize(final FileType fileType) {
    return 4;
  }

  @Nullable
  public String getLineIndent(@NotNull final Editor editor) {
    return null;
  }

  public String getLineSeparator() {
    return "\n";
  }

  public int getRightMargin() {
    return 80;
  }

  public int getTabSize(final FileType fileType) {
    return 4;
  }

  public boolean isSmartTabs(final FileType fileType) {
    return false;
  }

  public boolean projectUsesOwnSettings() {
    return false;
  }

  public boolean useTabCharacter(final FileType fileType) {
    return false;
  }
}