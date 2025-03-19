// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.roots.ui.util.SimpleTextCellAppearance;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.LightFilePointer;
import com.intellij.projectModel.ProjectModelBundle;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public final class OrderEntryAppearanceServiceImpl extends OrderEntryAppearanceService {

  @Override
  public @NotNull CellAppearanceEx forOrderEntry(Project project, final @NotNull OrderEntry orderEntry, final boolean selected) {
    if (orderEntry instanceof JdkOrderEntry jdkLibraryEntry) {
      Sdk jdk = jdkLibraryEntry.getJdk();
      if (!orderEntry.isValid()) {
        final String oldJdkName = jdkLibraryEntry.getJdkName();
        return FileAppearanceService.getInstance().forInvalidUrl(oldJdkName != null ? oldJdkName : JavaUiBundle.message("sdk.missing.item"));
      }
      return forJdk(jdk, false, selected, true);
    }
    else if (!orderEntry.isValid()) {
      return FileAppearanceService.getInstance().forInvalidUrl(orderEntry.getPresentableName());
    }
    else if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry) {
      if (!libraryOrderEntry.isValid()) { //library can be removed
        return FileAppearanceService.getInstance().forInvalidUrl(orderEntry.getPresentableName());
      }
      Library library = libraryOrderEntry.getLibrary();
      assert library != null : libraryOrderEntry;
      return forLibrary(project, library, !((LibraryEx)library).getInvalidRootUrls(OrderRootType.CLASSES).isEmpty());
    }
    else if (orderEntry.isSynthetic()) {
      String presentableName = orderEntry.getPresentableName();
      Icon icon = orderEntry instanceof ModuleSourceOrderEntry ? sourceFolderIcon(false) : null;
      return new SimpleTextCellAppearance(presentableName, icon, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
    else if (orderEntry instanceof ModuleOrderEntry entry) {
      final Icon icon = ModuleType.get(entry.getModule()).getIcon();
      return SimpleTextCellAppearance.regular(orderEntry.getPresentableName(), icon);
    }
    else {
      return CompositeAppearance.single(orderEntry.getPresentableName());
    }
  }

  @Override
  public @NotNull CellAppearanceEx forLibrary(Project project, final @NotNull Library library, final boolean hasInvalidRoots) {
    final StructureConfigurableContext context = ProjectStructureConfigurable.getInstance(project).getContext();
    final Icon icon = LibraryPresentationManager.getInstance().getCustomIcon(library, context);

    final String name = library.getName();
    if (name != null) {
      return normalOrRedWaved(name, (icon != null ? icon : PlatformIcons.LIBRARY_ICON), hasInvalidRoots);
    }

    final String[] files = library.getUrls(OrderRootType.CLASSES);
    if (files.length == 0) {
      return SimpleTextCellAppearance.invalid(ProjectModelBundle.message("empty.library.title"), PlatformIcons.LIBRARY_ICON);
    }
    else if (files.length == 1) {
      return forVirtualFilePointer(new LightFilePointer(files[0]));
    }

    final String url = StringUtil.trimEnd(files[0], JarFileSystem.JAR_SEPARATOR);
    String text = JavaUiBundle.message("library.unnamed.text", PathUtil.getFileName(url), files.length - 1);
    return SimpleTextCellAppearance.regular(text, PlatformIcons.LIBRARY_ICON);
  }

  @Override
  public @NotNull CellAppearanceEx forJdk(final @Nullable Sdk jdk, final boolean isInComboBox, final boolean selected, final boolean showVersion) {
    return SdkAppearanceService.getInstance().forSdk(jdk, isInComboBox, selected, showVersion);
  }

  @Override
  public @NotNull CellAppearanceEx forContentFolder(final @NotNull ContentFolder folder) {
    if (folder instanceof SourceFolder) {
      return formatRelativePath(folder, PlatformIcons.FOLDER_ICON);
    }
    else if (folder instanceof ExcludeFolder) {
      return formatRelativePath(folder, IconLoader.getDisabledIcon(PlatformIcons.FOLDER_ICON));
    }
    else {
      throw new RuntimeException(folder.getClass().getName());
    }
  }

  @Override
  public @NotNull CellAppearanceEx forModule(final @NotNull Module module) {
    return SimpleTextCellAppearance.regular(module.getName(), ModuleType.get(module).getIcon());
  }

  private static @NotNull Icon sourceFolderIcon(final boolean testSource) {
    return testSource ? PlatformIcons.TEST_SOURCE_FOLDER : PlatformIcons.SOURCE_FOLDERS_ICON;
  }

  private static @NotNull CellAppearanceEx normalOrRedWaved(final @NotNull @NlsContexts.Label String text, final @Nullable Icon icon, final boolean waved) {
    return waved ? new SimpleTextCellAppearance(text, icon, new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, null, JBColor.RED))
                 : SimpleTextCellAppearance.regular(text, icon);
  }

  private static @NotNull CellAppearanceEx forVirtualFilePointer(final @NotNull LightFilePointer filePointer) {
    final VirtualFile file = filePointer.getFile();
    return file != null ? FileAppearanceService.getInstance().forVirtualFile(file)
                        : FileAppearanceService.getInstance().forInvalidUrl(filePointer.getPresentableUrl());
  }

  private static @NotNull CellAppearanceEx formatRelativePath(final @NotNull ContentFolder folder, final @NotNull Icon icon) {
    LightFilePointer folderFile = new LightFilePointer(folder.getUrl());
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(folder.getContentEntry().getUrl());
    if (file == null) return FileAppearanceService.getInstance().forInvalidUrl(folderFile.getPresentableUrl());

    String contentPath = file.getPath();
    String relativePath;
    SimpleTextAttributes textAttributes;
    VirtualFile folderFileFile = folderFile.getFile();
    if (folderFileFile == null) {
      String absolutePath = folderFile.getPresentableUrl();
      relativePath = absolutePath.startsWith(contentPath) ? absolutePath.substring(contentPath.length()) : absolutePath;
      textAttributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
    else {
      relativePath = VfsUtilCore.getRelativePath(folderFileFile, file, File.separatorChar);
      textAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    }

    relativePath = StringUtil.isEmpty(relativePath) ? "." + File.separatorChar : relativePath;
    return new SimpleTextCellAppearance(relativePath, icon, textAttributes);
  }
}
