package com.intellij.ide.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class TreeFileChooserFactory {
  public static TreeFileChooserFactory getInstance(Project project) {
    return ServiceManager.getService(project, TreeFileChooserFactory.class);
  }

  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders);


  @NotNull
  public abstract TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents);
}
