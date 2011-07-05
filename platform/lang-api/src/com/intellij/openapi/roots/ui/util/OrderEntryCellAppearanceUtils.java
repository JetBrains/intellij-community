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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.LightFilePointer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class OrderEntryCellAppearanceUtils {
  public static final Icon EXCLUDE_FOLDER_ICON = CellAppearanceUtils.excludeIcon(PlatformIcons.FOLDER_ICON);
  public static final Icon GENERIC_JDK_ICON = IconLoader.getIcon("/general/jdk.png");
  public static final String NO_JDK = ProjectBundle.message("jdk.missing.item");

  private OrderEntryCellAppearanceUtils() {
  }

  public static CellAppearance forOrderEntry(OrderEntry orderEntry, boolean selected) {
    if (orderEntry instanceof JdkOrderEntry) {
      JdkOrderEntry jdkLibraryEntry = (JdkOrderEntry)orderEntry;
      Sdk jdk = jdkLibraryEntry.getJdk();
      if (!orderEntry.isValid()) {
        final String oldJdkName = jdkLibraryEntry.getJdkName();
        return SimpleTextCellAppearance.invalid(oldJdkName != null ? oldJdkName : NO_JDK,
                                                CellAppearanceUtils.INVALID_ICON);
      }
      return forJdk(jdk, false, selected);
    }
    else if (!orderEntry.isValid()) {
      return SimpleTextCellAppearance.invalid(orderEntry.getPresentableName(), CellAppearanceUtils.INVALID_ICON);
    }
    else if (orderEntry instanceof LibraryOrderEntry) {
      LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
      final Library library = libraryOrderEntry.getLibrary();
      if (!libraryOrderEntry.isValid()){ //library can be removed
        return SimpleTextCellAppearance.invalid(orderEntry.getPresentableName(), CellAppearanceUtils.INVALID_ICON);
      }
      return forLibrary(library);
    }
    else if (orderEntry.isSynthetic()) {
      String presentableName = orderEntry.getPresentableName();
      Icon icon = orderEntry instanceof ModuleSourceOrderEntry ? sourceFolderIcon(false) : null;
      return new SimpleTextCellAppearance(presentableName, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
    else if (orderEntry instanceof ModuleOrderEntry) {
      final Icon icon = ((ModuleOrderEntry)orderEntry).getModule().getModuleType().getNodeIcon(false);
      return SimpleTextCellAppearance.normal(orderEntry.getPresentableName(), icon);
    }
    else return CompositeAppearance.single(orderEntry.getPresentableName());
  }

  public static CellAppearance forLibrary(Library library) {
    return forLibrary(library, false);
  }

  public static CellAppearance forLibrary(Library library, final boolean hasInvalidRoots) {
    String name = library.getName();
    if (name != null) {
      return normalOrRedWaved(name, PlatformIcons.LIBRARY_ICON, hasInvalidRoots);
    }
    String[] files = library.getUrls(OrderRootType.CLASSES);
    if (files.length == 0) {
      return SimpleTextCellAppearance.invalid(ProjectBundle.message("library.empty.library.item"), PlatformIcons.LIBRARY_ICON);
    }
    if (files.length == 1) {
      return forVirtualFilePointer(new LightFilePointer(files[0]));
    }
    String url = StringUtil.trimEnd(files[0], JarFileSystem.JAR_SEPARATOR);
    return SimpleTextCellAppearance.normal(PathUtil.getFileName(url), PlatformIcons.LIBRARY_ICON);
  }

  public static CellAppearance normalOrRedWaved(String text, final Icon icon, boolean waved) {
    if (waved) {
      return new SimpleTextCellAppearance(text, icon, new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, null, Color.RED));
    }
    return SimpleTextCellAppearance.normal(text, icon);
  }

  public static Icon sourceFolderIcon(boolean testSource) {
    return testSource ? PlatformIcons.TEST_SOURCE_FOLDER : PlatformIcons.SOURCE_FOLDERS_ICON;
  }

  public static ModifiableCellAppearance forJdk(@Nullable Sdk jdk, boolean isInComboBox, final boolean selected, final boolean showVersion) {
    if (jdk == null) {
      return SimpleTextCellAppearance.invalid(NO_JDK, CellAppearanceUtils.INVALID_ICON);
    }
    String name = jdk.getName();
    CompositeAppearance appearance = new CompositeAppearance();
    appearance.setIcon(jdk.getSdkType().getIcon());
    VirtualFile homeDirectory = jdk.getHomeDirectory();
    SimpleTextAttributes attributes =
        homeDirectory != null && homeDirectory.isValid() ? CellAppearanceUtils.createSimpleCellAttributes(selected) : SimpleTextAttributes.ERROR_ATTRIBUTES;
    CompositeAppearance.DequeEnd ending = appearance.getEnding();
    ending.addText(name, attributes);
    if (showVersion) {
      String versionString = jdk.getVersionString();
      if (versionString != null && !versionString.equals(name)) {
        SimpleTextAttributes textAttributes = isInComboBox ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
        ending.addComment(versionString, textAttributes);
      }
    }
    return ending.getAppearance();
  }

  public static CellAppearance forJdk(@Nullable Sdk jdk, boolean isInComboBox, final boolean selected) {
    return forJdk(jdk, isInComboBox, selected, true);
  }

  public static SimpleTextCellAppearance forSourceFolder(SourceFolder folder) {
    return formatRelativePath(folder, PlatformIcons.FOLDER_ICON);
  }

  public static CellAppearance forExcludeFolder(ExcludeFolder folder) {
    return formatRelativePath(folder, EXCLUDE_FOLDER_ICON);
  }

  public static CellAppearance forContentFolder(ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      return forSourceFolder((SourceFolder)folder);
    }
    else if (folder instanceof ExcludeFolder) {
      return forExcludeFolder((ExcludeFolder)folder);
    }
    else {
      throw new RuntimeException(folder.getClass().getName());
    }
  }

  public static CellAppearance forModule(Module module) {
    return SimpleTextCellAppearance.normal(module.getName(), module.getModuleType().getNodeIcon(false));
  }

  public static CellAppearance forContentEntry(ContentEntry contentEntry) {
    return forVirtualFilePointer(new LightFilePointer(contentEntry.getUrl()));
  }

  public static SimpleTextCellAppearance formatRelativePath(ContentFolder folder, Icon icon) {
    LightFilePointer folderFile = new LightFilePointer(folder.getUrl());
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(folder.getContentEntry().getUrl());
    if (file == null) return forInvalidVirtualFilePointer(folderFile);
    String contentPath = file.getPath();
    String relativePath;
    SimpleTextAttributes textAttributes;
    if (!folderFile.isValid()) {
      textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
      String absolutePath = folderFile.getPresentableUrl();
      relativePath = absolutePath.startsWith(contentPath) ? absolutePath.substring(contentPath.length()) : absolutePath;
    }
    else {
      relativePath = VfsUtil.getRelativePath(folderFile.getFile(), file, File.separatorChar);
      textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }
    if (relativePath == null) relativePath = "";
    relativePath = relativePath.length() == 0 ? "." + File.separatorChar : relativePath;
    return new SimpleTextCellAppearance(relativePath, icon, textAttributes);
  }

  public static CellAppearance forProjectJdk(final Project project) {
    final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
    final Sdk projectJdk = projectRootManager.getProjectSdk();
    final CellAppearance appearance;
    if (projectJdk != null) {
      appearance = forJdk(projectJdk, false, false);
    }
    else {
      // probably invalid JDK
      final String projectJdkName = projectRootManager.getProjectSdkName();
      if (projectJdkName != null) {
        appearance = SimpleTextCellAppearance.invalid(ProjectBundle.message("jdk.combo.box.invalid.item", projectJdkName),
                                                      CellAppearanceUtils.INVALID_ICON);
      }
      else {
        appearance = forJdk(null, false, false);
      }
    }
    return appearance;
  }

  public static CellAppearance forVirtualFilePointer(LightFilePointer filePointer) {
    return filePointer.isValid() ?
           CellAppearanceUtils.forValidVirtualFile(filePointer.getFile()) :
           forInvalidVirtualFilePointer(filePointer);
  }

  static SimpleTextCellAppearance forInvalidVirtualFilePointer(LightFilePointer filePointer) {
    return SimpleTextCellAppearance.invalid(filePointer.getPresentableUrl(), CellAppearanceUtils.INVALID_ICON);
  }
}
