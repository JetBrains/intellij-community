// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditFileTypeOverrider implements FileTypeOverrider {

  private static final Key<Boolean> KEY = Key.create("LightEdit.PlainText");

  @Override
  public @Nullable FileType getOverriddenFileType(@NotNull VirtualFile file) {
    return isPlainText(file) ? PlainTextFileType.INSTANCE : null;
  }

  private static boolean isPlainText(@NotNull VirtualFile file) {
    return file.getUserData(KEY) == Boolean.TRUE;
  }

  static void markUnknownFileTypeAsPlainText(@NotNull VirtualFile file) {
    if (FileTypeRegistry.getInstance().isFileOfType(file, FileTypes.UNKNOWN)) {
      file.putUserData(KEY, Boolean.TRUE);
      FileContentUtilCore.reparseFiles(file);
    }
  }
}
