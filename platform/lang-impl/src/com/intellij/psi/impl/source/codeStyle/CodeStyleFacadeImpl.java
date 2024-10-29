// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class CodeStyleFacadeImpl extends CodeStyleFacade {
  private final @Nullable Project myProject;

  public CodeStyleFacadeImpl() {
    this(null);
  }

  public CodeStyleFacadeImpl(final @Nullable Project project) {
    myProject = project;
  }

  @Override
  @Deprecated
  public int getIndentSize(final FileType fileType) {
    return CodeStyle.getProjectOrDefaultSettings(myProject).getIndentSize(fileType);
  }

  @Override
  @Deprecated
  public @Nullable String getLineIndent(final @NotNull Document document, int offset) {
    if (myProject == null) return null;
    PsiDocumentManager.getInstance(myProject).commitDocument(document);
    return CodeStyleManager.getInstance(myProject).getLineIndent(document, offset);
  }

  @Override
  public String getLineIndent(@NotNull Editor editor, @Nullable Language language, int offset, boolean allowDocCommit) {
    return CodeStyle.getLineIndent(editor, language, offset, allowDocCommit);
  }


  @Override
  public boolean useTabCharacter(final FileType fileType) {
    return CodeStyle.getProjectOrDefaultSettings(myProject).useTabCharacter(fileType);
  }

}
