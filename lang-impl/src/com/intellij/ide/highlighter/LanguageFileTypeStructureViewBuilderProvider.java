/*
 * @author max
 */
package com.intellij.ide.highlighter;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewBuilderProvider;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LanguageFileTypeStructureViewBuilderProvider implements StructureViewBuilderProvider {
  @Nullable
  public StructureViewBuilder getStructureViewBuilder(@NotNull final FileType fileType, @NotNull final VirtualFile file, @NotNull final Project project) {
    if (fileType instanceof LanguageFileType) {
      final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
      if (psiFile == null) return null;
      final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(((LanguageFileType)fileType).getLanguage());
      return factory == null ?  null : factory.getStructureViewBuilder(psiFile);
    }

    return null;
  }
}