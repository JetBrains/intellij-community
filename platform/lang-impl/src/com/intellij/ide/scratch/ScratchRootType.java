// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Root for files placed under the "Scratches" folder in the
 * "Scratches and Consoles" section of the project view.
 */
public final class ScratchRootType extends RootType {
  public static @NotNull ScratchRootType getInstance() {
    return findByClass(ScratchRootType.class);
  }

  ScratchRootType() {
    super("scratches", LangBundle.message("root.type.scratches"));
  }

  @Override
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchFileService.getInstance().getScratchesMapping().getMapping(file);
  }

  @Override
  public @NotNull Icon patchIcon(@NotNull Icon baseIcon, @NotNull VirtualFile file, int flags, @Nullable Project project) {
      if (file.isDirectory()) return baseIcon;
      return new ScratchFileTypeIcon(baseIcon);
  }

  public @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                 @NotNull String fileName,
                                                 @Nullable Language language,
                                                 @NotNull String text) {
    return createScratchFile(project, fileName, language, text, ScratchFileService.Option.create_new_always);
  }

  public @Nullable VirtualFile createScratchFile(@Nullable Project project,
                                                 @NotNull String fileName,
                                                 @Nullable Language language,
                                                 @NotNull String text,
                                                 @NotNull ScratchFileService.Option option) {
    return ScratchFileActions.createScratchFile(project, fileName, language, text, option, this);
  }
}
