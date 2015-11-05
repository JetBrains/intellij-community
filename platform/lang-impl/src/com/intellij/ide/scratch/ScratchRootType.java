/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.scratch;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.UIBundle;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public final class ScratchRootType extends RootType {
  @NotNull
  public static ScratchRootType getInstance() {
    return findByClass(ScratchRootType.class);
  }

  ScratchRootType() {
    super("scratches", "Scratches");
  }

  @Override
  public Language substituteLanguage(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchFileService.getInstance().getScratchesMapping().getMapping(file);
  }

  @Nullable
  @Override
  public Icon substituteIcon(@NotNull Project project, @NotNull VirtualFile file) {
    Icon icon = ObjectUtils.chooseNotNull(super.substituteIcon(project, file), ScratchFileType.INSTANCE.getIcon());
    return LayeredIcon.create(icon, AllIcons.Actions.Scratch);
  }

  @Nullable
  public VirtualFile createScratchFile(Project project, final String fileName, final Language language, final String text) {
    return createScratchFile(project, fileName, language, text, ScratchFileService.Option.create_new_always);
  }

  @Nullable
  public VirtualFile createScratchFile(Project project,
                                       final String fileName,
                                       final Language language,
                                       final String text,
                                       final ScratchFileService.Option option) {
    RunResult<VirtualFile> result =
      new WriteCommandAction<VirtualFile>(project, UIBundle.message("file.chooser.create.new.file.command.name")) {
        @Override
        protected boolean isGlobalUndoAction() {
          return true;
        }

        @Override
        protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
          return UndoConfirmationPolicy.REQUEST_CONFIRMATION;
        }

        @Override
        protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
          ScratchFileService fileService = ScratchFileService.getInstance();
          VirtualFile file = fileService.findFile(ScratchRootType.this, fileName, option);
          fileService.getScratchesMapping().setMapping(file, language);
          VfsUtil.saveText(file, text);
          result.setResult(file);
        }
      }.execute();
    if (result.hasException()) {
      Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", fileName),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
      return null;
    }
    return result.getResultObject();
  }
}
