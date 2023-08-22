// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.ui.ProductIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Intended for use in actions related to opening or importing existing projects.
 * <strong>Due to a high I/O impact SHOULD NOT be used in any other cases.</strong>
 */
public class OpenProjectFileChooserDescriptor extends FileChooserDescriptor {
  public OpenProjectFileChooserDescriptor(boolean chooseFiles) {
    this(chooseFiles, chooseFiles);
  }

  public OpenProjectFileChooserDescriptor(boolean chooseFiles, boolean chooseJars) {
    super(chooseFiles, true, chooseJars, chooseJars, false, false);
    setHideIgnored(false);
  }

  @Override
  public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
    return super.isFileVisible(file, showHiddenFiles) && (file.isDirectory() || isProjectFile(file));
  }

  @Override
  public boolean isFileSelectable(@Nullable VirtualFile file) {
    return file != null && (isProjectDirectory(file) || isProjectFile(file));
  }

  @Override
  public Icon getIcon(VirtualFile file) {
    if (canInspectDirectory(file)) {
      if (isIprFile(file) || isIdeaDirectory(file)) {
        return dressIcon(file, ProductIcons.getInstance().getProjectNodeIcon());
      }
      Icon icon = getImporterIcon(file);
      if (icon != null) {
        return dressIcon(file, icon);
      }
    }
    return super.getIcon(file);
  }

  private static boolean canInspectDirectory(VirtualFile file) {
    if (file.isInLocalFileSystem()) {
      try {
        var path = file.getFileSystem().getNioPath(file);
        if (path != null && Path.of(SystemProperties.getUserHome()).startsWith(path)) {
          return false;
        }
      }
      catch (InvalidPathException e) {
        Logger.getInstance(OpenProjectFileChooserDescriptor.class).error(e);
      }
    }

    return true;
  }

  private static @Nullable Icon getImporterIcon(VirtualFile file) {
    var provider = ProjectOpenProcessor.getImportProvider(file);
    return provider == null ? null :
           file.isDirectory() && provider.lookForProjectsInDirectory() ? ProductIcons.getInstance().getProjectNodeIcon() :
           provider.getIcon(file);
  }

  public static boolean isProjectFile(@NotNull VirtualFile file) {
    return !file.isDirectory() && file.isValid() && (isIprFile(file) || hasImportProvider(file));
  }

  private static boolean isProjectDirectory(@NotNull VirtualFile file) {
    return file.isDirectory() && file.isValid() && (isIdeaDirectory(file) || hasImportProvider(file));
  }

  private static boolean isIprFile(VirtualFile file) {
    return ProjectFileType.DEFAULT_EXTENSION.equalsIgnoreCase(file.getExtension());
  }

  private static boolean isIdeaDirectory(VirtualFile file) {
    return ProjectKt.getProjectStoreDirectory(file) != null;
  }

  private static boolean hasImportProvider(VirtualFile file) {
    return ProjectOpenProcessor.getImportProvider(file) != null;
  }
}
