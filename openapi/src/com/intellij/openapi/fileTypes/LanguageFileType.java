package com.intellij.openapi.fileTypes;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 24, 2005
 * Time: 11:34:06 AM
 * To change this template use File | Settings | File Templates.
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

  public StructureViewModel getStructureViewModel(VirtualFile file, Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile != null ? myLanguage.getStructureViewModel(psiFile) : null;
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
