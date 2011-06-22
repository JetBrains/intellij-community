/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;

import javax.swing.*;
import java.io.File;

// Author: dyoma

public class CellAppearanceUtils {
  public static final Icon INVALID_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");
  public static final CellAppearance EMPTY = new EmptyAppearance();

  private CellAppearanceUtils() {
  }

  public static SimpleTextAttributes createSimpleCellAttributes(boolean isSelected){
    return isSelected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }


  public static CellAppearance forVirtualFile(VirtualFile virtualFile) {
    return virtualFile.isValid() ?
           forValidVirtualFile(virtualFile) :
           SimpleTextCellAppearance.invalid(virtualFile.getPresentableUrl(), INVALID_ICON);
  }

  public static CellAppearance forValidVirtualFile(VirtualFile virtualFile) {
    final VirtualFileSystem fileSystem = virtualFile.getFileSystem();
    if (fileSystem.getProtocol().equals(JarFileSystem.PROTOCOL)) {
      return new JarSubfileCellAppearance(virtualFile);
    }
    if (fileSystem instanceof HttpFileSystem) {
      return new HttpUrlCellAppearance(virtualFile);
    }
    if (virtualFile.isDirectory()) {
      return SimpleTextCellAppearance.normal(virtualFile.getPresentableUrl(), PlatformIcons.FOLDER_ICON);
    }
    return new ValidFileCellAppearance(virtualFile);
  }

  public static Icon iconForFile(VirtualFile file) {
    if (file.getFileSystem().getProtocol().equals(JarFileSystem.PROTOCOL) && file.getParent() == null) {
      return file.getIcon();
    }
    if (file.isDirectory()) return PlatformIcons.FOLDER_ICON;
    return file.getIcon();
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

  private static class EmptyAppearance implements CellAppearance {
    public void customize(SimpleColoredComponent component) {
    }

    public String getText() {
      return "";
    }
  }
}
