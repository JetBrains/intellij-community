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

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author gregsh
 */
public class ScratchUtil {
  private ScratchUtil() {
  }

  /**
   * Returns true if a file is in one of scratch roots: scratch, console, etc.
   * @see RootType
   * @see ScratchFileService
   */
  public static boolean isScratch(@Nullable VirtualFile file) {
    return file != null && file.getFileType() == ScratchFileType.INSTANCE;
  }

  public static void updateFileExtension(@NotNull Project project, @Nullable VirtualFile file) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      throw new AssertionError("command required");
    }

    if (file == null) return;
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    String extension = file.getExtension();
    FileType expected = extension == null ? null : FileTypeManager.getInstance().getFileTypeByExtension(extension);
    FileType actual = language == null ? null : language.getAssociatedFileType();
    if (expected == actual || actual == null) return;
    String ext = actual.getDefaultExtension();
    if (StringUtil.isEmpty(ext)) return;

    String newName = PathUtil.makeFileName(file.getNameWithoutExtension(), ext);
    VirtualFile parent = file.getParent();
    newName = parent != null && parent.findChild(newName) != null ? PathUtil.makeFileName(file.getName(), ext) : newName;
    file.rename(ScratchUtil.class, newName);
  }

  public static boolean hasMatchingExtension(@NotNull Project project, @NotNull VirtualFile file) {
    String extension = file.getExtension();
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    FileType expected = extension == null ? null : FileTypeManager.getInstance().getFileTypeByExtension(extension);
    FileType actual = language == null ? null : language.getAssociatedFileType();
    return expected == actual && actual != null;
  }
}
