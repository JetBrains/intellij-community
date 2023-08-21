// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class TreeFileChooserFactoryImpl extends TreeFileChooserFactory {
  private final Project myProject;

  public TreeFileChooserFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, false, false);
  }

  @Override
  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                           boolean disableStructureProviders) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, false);
  }

  @Override
  @NotNull
  public TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                           boolean disableStructureProviders,
                                           boolean showLibraryContents) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, disableStructureProviders, showLibraryContents);
  }
}
