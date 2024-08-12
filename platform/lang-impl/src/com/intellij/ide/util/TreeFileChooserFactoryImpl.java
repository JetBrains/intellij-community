// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public final class TreeFileChooserFactoryImpl extends TreeFileChooserFactory {
  private final Project myProject;

  public TreeFileChooserFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter) {
    return createFileChooser(title, initialFile, fileType, filter, false);
  }

  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders) {
    return createFileChooser(title, initialFile, fileType, filter, disableStructureProviders, false);
  }

  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull String title, @Nullable PsiFile initialFile, @Nullable FileType fileType, @Nullable TreeFileChooser.PsiFileFilter filter,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents) {
    return createFileChooser(title, initialFile, fileType, filter, null, disableStructureProviders, showLibraryContents);
  }

  @Override
  public @NotNull TreeFileChooser createFileChooser(@NotNull String title,
                                                    @Nullable PsiFile initialFile,
                                                    @Nullable FileType fileType,
                                                    TreeFileChooser.@Nullable PsiFileFilter filter,
                                                    @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                                    boolean disableStructureProviders,
                                                    boolean showLibraryContents) {
    return new TreeFileChooserDialog(myProject, title, initialFile, fileType, filter, comparator, disableStructureProviders, showLibraryContents);
  }
}
