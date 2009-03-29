package com.intellij.ide.util;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class TreeFileChooserFactoryImpl extends TreeFileChooserFactory {
  private Project myProject;

  public TreeFileChooserFactoryImpl(Project project) {
    myProject = project;
  }

  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, false, false);
  }

  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                           boolean disableStructureProviders) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, false);
  }

  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                           boolean disableStructureProviders,
                                           boolean showLibraryContents) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, showLibraryContents);
  }
}
