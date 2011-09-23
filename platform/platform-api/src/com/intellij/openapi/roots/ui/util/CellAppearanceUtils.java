/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.util;

import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.io.File;

/**
 * @deprecated please use {@linkplain com.intellij.openapi.roots.ui.FileAppearanceService} (to remove in IDEA 12)
 */
@SuppressWarnings("UnusedDeclaration")
public class CellAppearanceUtils {
  public static final Icon INVALID_ICON = PlatformIcons.INVALID_ENTRY_ICON;
  public static final CellAppearance EMPTY = FileAppearanceService.getInstance().empty();

  private CellAppearanceUtils() { }

  public static SimpleTextAttributes createSimpleCellAttributes(boolean isSelected) {
    return isSelected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }

  public static CellAppearance forVirtualFile(VirtualFile virtualFile) {
    return FileAppearanceService.getInstance().forVirtualFile(virtualFile);
  }

  public static CellAppearance forValidVirtualFile(VirtualFile virtualFile) {
    return FileAppearanceService.getInstance().forVirtualFile(virtualFile);
  }

  public static Icon iconForFile(VirtualFile file) {
    if (file.getFileSystem().getProtocol().equals(JarFileSystem.PROTOCOL) && file.getParent() == null) {
      return VirtualFilePresentation.getIcon(file);
    }
    if (file.isDirectory()) return PlatformIcons.FOLDER_ICON;
    return VirtualFilePresentation.getIcon(file);
  }

  public static Icon excludeIcon(Icon icon) {
    return IconLoader.getDisabledIcon(icon);
  }

  public static CompositeAppearance forFile(File file) {
    String absolutePath = file.getAbsolutePath();
    if (!file.exists()) return CompositeAppearance.invalid(absolutePath);
    if (file.isDirectory()) {
      CompositeAppearance appearance = CompositeAppearance.single(absolutePath);
      appearance.setIcon(PlatformIcons.FOLDER_ICON);
      return appearance;
    }
    String name = file.getName();
    FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(name);
    File parent = file.getParentFile();
    CompositeAppearance appearance = CompositeAppearance.textComment(name, parent.getAbsolutePath());
    appearance.setIcon(fileType.getIcon());
    return appearance;
  }
}
