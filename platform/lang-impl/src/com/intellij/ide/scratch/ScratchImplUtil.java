// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author gregsh
 */
public final class ScratchImplUtil {
  private ScratchImplUtil() {
  }

  public static void updateFileExtension(@NotNull Project project, @Nullable VirtualFile file) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (CommandProcessor.getInstance().getCurrentCommand() == null) {
      throw new AssertionError("command required");
    }

    if (file == null) return;
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    FileType prevType = getFileTypeFromName(file);
    FileType currType = language == null ? null : language.getAssociatedFileType();
    if (prevType == currType) return;
    String prevExt = PathUtil.makeFileName("", prevType == null ? "" : prevType.getDefaultExtension());
    String currExt = currType == null ? "" : currType.getDefaultExtension();
    // support multipart extensions like *.blade.php
    String nameWithoutExtension = prevExt.length() > 0 && file.getName().endsWith(prevExt) ?
                                  StringUtil.trimEnd(file.getName(), prevExt) : file.getNameWithoutExtension();
    VirtualFile parent = file.getParent();
    String newName = parent != null ? VfsUtil.getNextAvailableName(parent, nameWithoutExtension, currExt) :
                     PathUtil.makeFileName(nameWithoutExtension, currExt);
    file.rename(ScratchUtil.class, newName);
  }

  public static boolean hasMatchingExtension(@NotNull Project project, @NotNull VirtualFile file) {
    if (file.getExtension() == null) return true;
    FileType expected = getFileTypeFromName(file);
    Language language = LanguageUtil.getLanguageForPsi(project, file);
    FileType actual = language == null ? null : language.getAssociatedFileType();
    if (expected != null && expected == actual) return true;
    String ext = actual == null ? "" : actual.getDefaultExtension();
    return ext.length() > 0 && file.getName().endsWith(ext);
  }

  @Nullable
  private static FileType getFileTypeFromName(@NotNull VirtualFile file) {
    if (file.getExtension() == null) return null;
    FileType result = FileTypeManager.getInstance().getFileTypeByFileName(file.getNameSequence());
    if (result == UnknownFileType.INSTANCE || StringUtil.isEmpty(result.getDefaultExtension())) {
      return null;
    }
    return result;
  }

  @NotNull
  public static String getRelativePath(@NotNull Project project, @NotNull VirtualFile file) {
    return ScratchUtil.getRelativePath(project, file);
  }
}
