package com.intellij.codeInsight.template;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 28, 2008
 * Time: 4:53:26 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class FileTypeBasedContextType extends TemplateContextType {
  private final LanguageFileType myFileType;

  protected FileTypeBasedContextType(@NotNull @NonNls String id, @NotNull String presentableName, @NotNull LanguageFileType fileType) {
    super(id, presentableName);
    myFileType = fileType;
  }

  public boolean isInContext(@NotNull final PsiFile file, final int offset) {
    return isInContext(file.getFileType());
  }

  public boolean isInContext(@NotNull final FileType fileType) {
    return fileType == myFileType;
  }

  public SyntaxHighlighter createHighlighter() {
    return SyntaxHighlighter.PROVIDER.create(myFileType, null, null);
  }
}
