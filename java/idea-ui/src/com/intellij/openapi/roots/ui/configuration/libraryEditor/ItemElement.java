// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IconUtilEx;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;


class ItemElement extends LibraryTableTreeContentElement<ItemElement> {
  protected final String myUrl;
  private final OrderRootType myRootType;

  public ItemElement(@NotNull OrderRootTypeElement parent, @NotNull String url, @NotNull OrderRootType rootType, final boolean isJarDirectory,
                     boolean isValid) {
    super(parent);
    myUrl = url;
    myName = getPresentablePath(url).replace('/', File.separatorChar);
    myColor = getForegroundColor(isValid);
    setIcon(getIconForUrl(url, isValid, isJarDirectory));
    myRootType = rootType;
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
              icon = PlatformIcons.FOLDER_ICON;
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

  public static String getPresentablePath(final String url) {
    String presentablePath = VirtualFileManager.extractPath(url);
    if (isJarFileRoot(url)) {
      presentablePath = presentablePath.substring(0, presentablePath.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return presentablePath;
  }

  private static boolean isJarFileRoot(final String url) {
    return VirtualFileManager.extractPath(url).endsWith(JarFileSystem.JAR_SEPARATOR);
  }

  public OrderRootTypeElement getParent() {
    return (OrderRootTypeElement)getParentDescriptor();
  }

  @NotNull
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

  @NotNull
  public String getUrl() {
    return myUrl;
  }

  public int hashCode() {
    int result;
    result = getParent().hashCode();
    result = 29 * result + myUrl.hashCode();
    result = 29 * result + myRootType.hashCode();
    return result;
  }
}
