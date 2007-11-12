/*
 * @author max
 */
package com.intellij.codeStyle;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeStyleFacade {
  public static CodeStyleFacade getInstance() {
    return ServiceManager.getService(CodeStyleFacade.class);
  }

  public static CodeStyleFacade getInstance(Project project) {
    return ServiceManager.getService(project, CodeStyleFacade.class);
  }

  /**
   * Calculates the indent that should be used for the current line in the specified
   * editor.
   *
   * @param editor the editor for which the indent should be calculated.
   * @return the indent string (containing of tabs and/or whitespaces), or null if it
   *         was not possible to calculate the indent.
   */
  @Nullable
  public abstract String getLineIndent(@NotNull Editor editor);


  public abstract int getIndentSize(FileType fileType);

  public abstract boolean isSmartTabs(final FileType fileType);

  public abstract int getRightMargin();

  public abstract int getTabSize(final FileType fileType);

  public abstract boolean useTabCharacter(final FileType fileType);

  public abstract String getLineSeparator();

  public abstract boolean projectUsesOwnSettings();
}