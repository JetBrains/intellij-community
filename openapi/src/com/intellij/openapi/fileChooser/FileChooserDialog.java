/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public interface FileChooserDialog {
  VirtualFile[] choose(VirtualFile toSelect, Project project);
}