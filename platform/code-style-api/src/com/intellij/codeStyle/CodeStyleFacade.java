// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated See deprecations for specific methods, use methods of {@link com.intellij.application.options.CodeStyle} instead.
 */
@Deprecated(forRemoval = true)
public abstract class CodeStyleFacade {
  public static CodeStyleFacade getInstance() {
    return ApplicationManager.getApplication().getService(CodeStyleFacade.class);
  }

  public static CodeStyleFacade getInstance(@Nullable Project project) {
    if (project == null) return getInstance();
    return project.getService(CodeStyleFacade.class);
  }

  /**
   * @deprecated Use {@link com.intellij.application.options.CodeStyle#getLineIndent(Editor, Language, int, boolean)} instead.
   */
  @Nullable
  @Deprecated(forRemoval = true)
  public abstract String getLineIndent(@NotNull Document document, int offset);

  /**
   * @deprecated Use {@link com.intellij.application.options.CodeStyle#getLineIndent(Editor, Language, int, boolean)}
   */
  @Deprecated(forRemoval = true)
  @Nullable
  public String getLineIndent(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    return getLineIndent(editor.getDocument(), offset);
  }

  /**
   * @deprecated Use {@link com.intellij.application.options.CodeStyle#getIndentSize(PsiFile)} instead.
   */
  @Deprecated(forRemoval = true)
  public abstract int getIndentSize(FileType fileType);

  /**
   * @deprecated Use {@link com.intellij.application.options.CodeStyle#getFacade} and
   * {@link CodeStyleSettingsFacade#getTabSize()}
   */
  @Deprecated(forRemoval = true)
  public abstract int getTabSize(final FileType fileType);

  /**
   * @deprecated Use {@code CodeStyle.getIndentOptions(PsiFile).USE_TAB_CHARACTER}. See {@code CodeStyle for more information}
   */
  @Deprecated(forRemoval = true)
  public abstract boolean useTabCharacter(final FileType fileType);

  /**
   * @deprecated Use {@code getLineSeparator()} method of {@code CodeStyle.getSettings(PsiFile)} or
   *             {@code CodeStyle.getSettings(Project)} if there is no {@code PsiFile}
   */
  @Deprecated(forRemoval = true)
  public abstract String getLineSeparator();

}
