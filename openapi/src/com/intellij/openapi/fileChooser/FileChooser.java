/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;

public class FileChooser {
  private FileChooser() {}

  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor) {
    return chooseFiles(project, descriptor, null);
  }

  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor) {
    return chooseFiles(parent, descriptor, null);
  }

  public static VirtualFile[] chooseFiles(Project project, FileChooserDescriptor descriptor, VirtualFile toSelect) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
    return chooser.choose(toSelect, project);
  }

  public static VirtualFile[] chooseFiles(Component parent, FileChooserDescriptor descriptor, VirtualFile toSelect) {
    FileChooserDialog chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, parent);
    return chooser.choose(toSelect, null);
  }
}