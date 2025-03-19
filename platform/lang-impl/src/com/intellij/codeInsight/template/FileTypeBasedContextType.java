// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.template;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author lesya
 */
public abstract class FileTypeBasedContextType extends TemplateContextType {
  private final LanguageFileType myFileType;

  protected FileTypeBasedContextType(@NotNull @NlsContexts.Label String presentableName, @NotNull LanguageFileType fileType) {
    super(presentableName);
    myFileType = fileType;
  }

  /**
   * @deprecated Set contextId in plugin.xml instead
   */
  @Deprecated
  protected FileTypeBasedContextType(@NotNull @NonNls String id, @NotNull @NlsContexts.Label String presentableName, @NotNull LanguageFileType fileType) {
    super(id, presentableName);
    myFileType = fileType;
  }

  @Override
  public boolean isInContext(final @NotNull PsiFile file, final int offset) {
    return myFileType == file.getFileType();
  }

  @Override
  public SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(myFileType, null, null);
  }
}
