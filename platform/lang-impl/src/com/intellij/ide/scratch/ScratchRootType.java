// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

/**
 * @author gregsh
 */
public final class ScratchRootType extends RootType {
  @NotNull
  public static ScratchRootType getInstance() {
    return findByClass(ScratchRootType.class);
  }

  ScratchRootType() {
    super("scratches", LangBundle.message("root.type.scratches"));
  }

  @Override
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchFileService.getInstance().getScratchesMapping().getMapping(file);
  }

  @Nullable
  @Override
  public Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.isDirectory()) return null;
    Icon icon = ObjectUtils.notNull(super.substituteIcon(project, file), AllIcons.FileTypes.Text);
    return new ScratchFileTypeIcon(icon);
  }

  @Nullable
  public VirtualFile createScratchFile(@Nullable Project project,
                                       @NotNull String fileName,
                                       @Nullable Language language,
                                       @NotNull String text) {
    return createScratchFile(project, fileName, language, text, ScratchFileService.Option.create_new_always);
  }

  @Nullable
  public VirtualFile createScratchFile(@Nullable Project project,
                                       @NotNull String fileName,
                                       @Nullable Language language,
                                       @NotNull String text,
                                       @NotNull ScratchFileService.Option option) {
    try {
      return
        WriteCommandAction.writeCommandAction(project).withName(UIBundle.message("file.chooser.create.new.scratch.file.command.name"))
                          .withGlobalUndo().shouldRecordActionForActiveDocument(false)
                          .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION).compute(() -> {
          ScratchFileService fileService = ScratchFileService.getInstance();
          VirtualFile file = fileService.findFile(this, fileName, option);
          // save text should go before any other manipulations that load document,
          // otherwise undo will be broken
          VfsUtil.saveText(file, text);
          if (language != null) {
            Language fileLanguage = LanguageUtil.getFileLanguage(file);
            fileService.getScratchesMapping().setMapping(file, fileLanguage == null || language == fileLanguage ? null : language);
          }
          return file;
        });
    }
    catch (IOException e) {
      Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", fileName),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      return null;
    }
  }
}
