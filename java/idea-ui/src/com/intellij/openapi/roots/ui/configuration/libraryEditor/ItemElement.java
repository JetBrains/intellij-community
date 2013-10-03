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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.JBColor;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.io.File;


class ItemElement extends LibraryTableTreeContentElement<ItemElement> {
  private final String myUrl;
  private final OrderRootType myRootType;

  public ItemElement(OrderRootTypeElement parent, String url, OrderRootType rootType, final boolean isJarDirectory,
                     boolean isValid) {
    super(parent);
    myUrl = url;
    myRootType = rootType;
    myName = getPresentablePath(url).replace('/', File.separatorChar);
    myColor = isValid ? UIUtil.getListForeground() : JBColor.RED;
    setIcon(getIconForUrl(url, isValid, isJarDirectory));
  }

  public OrderRootTypeElement getParent() {
    return (OrderRootTypeElement)getParentDescriptor();
  }

  @Override
  public boolean update() {
    return false;
  }

  @Override
  public ItemElement getElement() {
    return this;
  }

  public String getUrl() {
    return myUrl;
  }

  public OrderRootType getRootType() {
    return myRootType;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ItemElement)) return false;

    final ItemElement itemElement = (ItemElement)o;

    if (!getParent().equals(itemElement.getParent())) return false;
    if (!myRootType.equals(itemElement.myRootType)) return false;
    if (!myUrl.equals(itemElement.myUrl)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = getParent().hashCode();
    result = 29 * result + myUrl.hashCode();
    result = 29 * result + myRootType.hashCode();
    return result;
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

  private static String getPresentablePath(final String url) {
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
