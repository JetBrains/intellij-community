// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.util;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public abstract class TreeFileChooserFactory {
  public static TreeFileChooserFactory getInstance(Project project) {
    return project.getService(TreeFileChooserFactory.class);
  }

  public abstract @NotNull TreeFileChooser createFileChooser(@NlsContexts.DialogTitle @NotNull String title,
                                                             @Nullable PsiFile initialFile,
                                                             @Nullable FileType fileType,
                                                             @Nullable TreeFileChooser.PsiFileFilter filter);


  public abstract @NotNull TreeFileChooser createFileChooser(@NlsContexts.DialogTitle @NotNull String title,
                                                             @Nullable PsiFile initialFile,
                                                             @Nullable FileType fileType,
                                                             @Nullable TreeFileChooser.PsiFileFilter filter,
                                                             boolean disableStructureProviders);


  public abstract @NotNull TreeFileChooser createFileChooser(@NlsContexts.DialogTitle @NotNull String title,
                                                             @Nullable PsiFile initialFile,
                                                             @Nullable FileType fileType,
                                                             @Nullable TreeFileChooser.PsiFileFilter filter,
                                                             boolean disableStructureProviders,
                                                             boolean showLibraryContents);

  public abstract @NotNull TreeFileChooser createFileChooser(@NlsContexts.DialogTitle @NotNull String title,
                                                             @Nullable PsiFile initialFile,
                                                             @Nullable FileType fileType,
                                                             @Nullable TreeFileChooser.PsiFileFilter filter,
                                                             @Nullable Comparator<? super NodeDescriptor<?>> comparator,
                                                             boolean disableStructureProviders,
                                                             boolean showLibraryContents);
}
