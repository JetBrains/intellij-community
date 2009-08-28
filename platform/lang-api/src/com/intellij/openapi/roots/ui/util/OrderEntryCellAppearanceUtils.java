package com.intellij.openapi.roots.ui.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.LightFilePointer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Icons;

import javax.swing.*;
import java.io.File;

public class OrderEntryCellAppearanceUtils {
  public static final Icon EXCLUDE_FOLDER_ICON = CellAppearanceUtils.excludeIcon(Icons.FOLDER_ICON);
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
    String name = library.getName();
    if (name != null) {
      return SimpleTextCellAppearance.normal(name, Icons.LIBRARY_ICON);
    }
    String[] files = library.getUrls(OrderRootType.CLASSES);
    if (files.length == 0) {
      return SimpleTextCellAppearance.invalid(ProjectBundle.message("library.empty.library.item"), Icons.LIBRARY_ICON);
    }
    if (files.length == 1) {
      return forVirtualFilePointer(new LightFilePointer(files[0]));
    }
    String url = files[0];
    if (url.endsWith(JarFileSystem.JAR_SEPARATOR)) {
      url = url.substring(0, url.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    int startIndex = Math.min(url.lastIndexOf('/') + 1, url.length() - 1);
    return SimpleTextCellAppearance.normal(url.substring(startIndex), Icons.LIBRARY_ICON);
  }

  public static Icon sourceFolderIcon(boolean testSource) {
    return testSource ? Icons.TEST_SOURCE_FOLDER : Icons.SOURCE_FOLDERS_ICON;
  }

  public static CellAppearance forJdk(Sdk jdk, boolean isInComboBox, final boolean selected) {
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
    String versionString = jdk.getVersionString();
    if (versionString != null && !versionString.equals(name)) {
      SimpleTextAttributes textAttributes = isInComboBox ? SimpleTextAttributes.SYNTHETIC_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES;
      ending.addComment(versionString, textAttributes);
    }
    return ending.getAppearance();
  }

  public static SimpleTextCellAppearance forSourceFolder(SourceFolder folder) {
    return formatRelativePath(folder, Icons.FOLDER_ICON);
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
    final Sdk projectJdk = projectRootManager.getProjectJdk();
    final CellAppearance appearance;
    if (projectJdk != null) {
      appearance = forJdk(projectJdk, false, false);
    }
    else {
      // probably invalid JDK
      final String projectJdkName = projectRootManager.getProjectJdkName();
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
