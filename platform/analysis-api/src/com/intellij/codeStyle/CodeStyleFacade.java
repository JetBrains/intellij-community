// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CodeStyleFacade {
  public static CodeStyleFacade getInstance() {
    return ServiceManager.getService(CodeStyleFacade.class);
  }

  public static CodeStyleFacade getInstance(@Nullable Project project) {
    if (project == null) return getInstance();
    return ServiceManager.getService(project, CodeStyleFacade.class);
  }

  /**
   * Calculates the indent that should be used for the line at specified offset in the specified
   * document.
   *
   * @param document the document for which the indent should be calculated.
   * @param offset the caret offset in the editor.
   * @return the indent string (containing of tabs and/or white spaces), or null if it
   *         was not possible to calculate the indent.
   * @deprecated Use {@link #getLineIndent(Editor, Language, int, boolean)} instead.
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Nullable
  @Deprecated
  public abstract String getLineIndent(@NotNull Document document, int offset);
  
  /**
   * Calculates the indent that should be used for the line at specified offset in the specified
   * editor. If there is a suitable {@code LineIndentProvider} for the language, it will be used to calculate the indent. Otherwise, if
   * {@code allowDocCommit} flag is true, the method will use formatter on committed document.
   *
   * @param editor   The editor for which the indent must be returned.
   * @param language Context language
   * @param offset   The caret offset in the editor.
   * @param allowDocCommit Allow calculation using committed document.
   *                       <p>
   *                         <b>NOTE: </b> Committing the document may be slow an cause performance issues on large files.
   * @return the indent string (containing of tabs and/or white spaces), or null if it
   *         was not possible to calculate the indent.
   */
  @Nullable
  public String getLineIndent(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    return getLineIndent(editor.getDocument(), offset);
  }

  /**
   * @deprecated Use {@link com.intellij.application.options.CodeStyle#getIndentSize(PsiFile)} instead.
   */
  @Deprecated
  public abstract int getIndentSize(FileType fileType);

  /**
   * Calculates the spacing (in columns) for joined lines at given offset after join lines or smart backspace actions.
   * If there is a suitable {@code LineIndentProvider} for the language,
   * it will be used to calculate the spacing. Otherwise, if
   * {@code allowDocCommit} flag is true, the method will use formatter on committed document.
   *
   * @param editor   The editor for which the spacing must be returned.
   * @param language Context language
   * @param offset   Offset in the editor after the indent in the second joining line.
   * @param allowDocCommit Allow calculation using committed document.
   *                       <p>
   *                         <b>NOTE: </b> Committing the document may be slow an cause performance issues on large files.
   * @return non-negative spacing between end- and start-line tokens after the join.
   */
  @ApiStatus.Experimental
  public abstract int getJoinedLinesSpacing(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit);

  /**
   * @deprecated Use {@code CodeStyle.getIndentOptions(PsiFile).TAB_SIZE}. See {@code CodeStyle for more information}
   */
  @Deprecated
  public abstract int getTabSize(final FileType fileType);

  /**
   * @deprecated Use {@code CodeStyle.getIndentOptions(PsiFile).USE_TAB_CHARACTER}. See {@code CodeStyle for more information}
   */
  @Deprecated
  public abstract boolean useTabCharacter(final FileType fileType);

  /**
   * @deprecated Use {@code getLineSeparator()} method of {@code CodeStyle.getSettings(PsiFile)} or
   *             {@code CodeStyle.getSettings(Project)} if there is no {@code PsiFile}
   */
  @Deprecated
  public abstract String getLineSeparator();

  /**
   * Determines whether a comma before space is needed for given {@code PsiFile} in given {@code Language} context.
   *
   * @param psiFile  File to determine settings
   * @param language Context language
   * @return whether the space before comma is needed
   */
  public abstract boolean useSpaceBeforeComma(@NotNull PsiFile psiFile, @NotNull Language language);

  /**
   * Determines whether a comma after space is needed for given {@code PsiFile} in given {@code Language} context.
   *
   * @param psiFile  File to determine settings
   * @param language Context language
   * @return whether the space after comma is needed
   */
  public abstract boolean useSpaceAfterComma(@NotNull PsiFile psiFile, @NotNull Language language);

  /**
   * Determines whether assignment operator should be surrounded with spaces for given {@code PsiFile} in given {@code Language} context.
   *
   * @param psiFile  File to determine settings
   * @param language Context language
   * @return whether to surround assignment operator with spaces
   */
  public abstract boolean useSpaceAroundAssignmentOperators(@NotNull PsiFile psiFile, @NotNull Language language);
}
