/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.presentation;

import com.intellij.ide.TypePresentationService;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IconUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class VirtualFilePresentation {

  public static Icon getIcon(@NotNull VirtualFile vFile) {
    return IconUtil.getIcon(vFile, 0, null);
  }

  public static Icon getIconImpl(@NotNull VirtualFile vFile) {
    Icon icon = TypePresentationService.getService().getIcon(vFile);
    if (icon != null) {
      return icon;
    }
    FileType fileType = vFile.getFileType();
    if (fileType == UnknownFileType.INSTANCE && vFile.isDirectory() && vFile.isInLocalFileSystem()) {
      return PlatformIcons.FOLDER_ICON;
    }
    return fileType.getIcon();
  }
}
