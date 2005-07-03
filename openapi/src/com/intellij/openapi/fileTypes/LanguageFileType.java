package com.intellij.openapi.fileTypes;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.Nullable;

/**
 * Kind of file types capable to provide {@link Language}.
 */
public abstract class LanguageFileType implements FileType{
  private Language myLanguage;

  protected LanguageFileType(final Language language) {
    myLanguage = language;
  }

  public final Language getLanguage() {
    return myLanguage;
  }

  public final SyntaxHighlighter getHighlighter(Project project) {
    return myLanguage.getSyntaxHighlighter(project);
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(VirtualFile file, Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile == null ?  null : myLanguage.getStructureViewBuilder(psiFile);
  }

  public final boolean isBinary() {
    return false;
  }

  public final boolean isReadOnly() {
    return false;
  }

  public String getCharset(VirtualFile file) {
    return null;
  }
}
