/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

class ItemElementDescriptor extends NodeDescriptor<ItemElement> {
  private final ItemElement myElement;

    public ItemElementDescriptor(NodeDescriptor parentDescriptor, ItemElement element) {
      super(null, parentDescriptor);
      myElement = element;
      final String url = element.getUrl();
      myName = getPresentablePath(url).replace('/', File.separatorChar);
      setIcon(getIconForUrl(url, element.isValid(), element.isJarDirectory()));
    }

    @Override
    public boolean update() {
      Color color = myElement.isValid()? UIUtil.getListForeground() : JBColor.RED;
      final boolean changes = !color.equals(myColor);
      myColor = color;
      return changes;
    }

    @Override
    public ItemElement getElement() {
      return myElement;
    }

  private static Icon getIconForUrl(final String url, final boolean isValid, final boolean isJarDirectory) {
    final Icon icon;
    if (isValid) {
      VirtualFile presentableFile;
      if (isJarFileRoot(url)) {
        presentableFile = LocalFileSystem.getInstance().findFileByPath(getPresentablePath(url));
      }
      else {
        presentableFile = VirtualFileManager.getInstance().findFileByUrl(url);
      }
      if (presentableFile != null && presentableFile.isValid()) {
        if (presentableFile.getFileSystem() instanceof HttpFileSystem) {
          icon = PlatformIcons.WEB_ICON;
        }
        else {
          if (presentableFile.isDirectory()) {
            if (isJarDirectory) {
              icon = AllIcons.Nodes.JarDirectory;
            }
            else {
              icon = PlatformIcons.DIRECTORY_CLOSED_ICON;
            }
          }
          else {
            icon = IconUtilEx.getIcon(presentableFile, 0, null);
          }
        }
      }
      else {
        icon = AllIcons.Nodes.PpInvalid;
      }
    }
    else {
      icon = AllIcons.Nodes.PpInvalid;
    }
    return icon;
  }

  static String getPresentablePath(final String url) {
    String presentablePath = VirtualFileManager.extractPath(url);
    if (isJarFileRoot(url)) {
      presentablePath = presentablePath.substring(0, presentablePath.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return presentablePath;
  }

  private static boolean isJarFileRoot(final String url) {
    return VirtualFileManager.extractPath(url).endsWith(JarFileSystem.JAR_SEPARATOR);
  }
}
