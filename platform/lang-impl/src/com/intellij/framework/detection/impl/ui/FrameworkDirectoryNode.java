// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.PlatformIcons;

import javax.swing.tree.TreeNode;
import java.io.File;

final class FrameworkDirectoryNode extends DetectedFrameworkTreeNodeBase {
  private static final Logger LOG = Logger.getInstance(FrameworkDirectoryNode.class);
  private final VirtualFile myDirectory;

  FrameworkDirectoryNode(VirtualFile directory) {
    super(directory);
    myDirectory = directory;
  }

  @Override
  public void renderNode(ColoredTreeCellRenderer renderer) {
    renderer.setIcon(PlatformIcons.FOLDER_ICON);
    renderer.append(getRelativePath());
  }

  private @NlsSafe String getRelativePath() {
    final TreeNode parent = getParent();
    String path;
    if (parent instanceof FrameworkDirectoryNode) {
      final VirtualFile parentDir = ((FrameworkDirectoryNode)parent).myDirectory;
      path = VfsUtilCore.getRelativePath(myDirectory, parentDir, File.separatorChar);
      LOG.assertTrue(path != null, myDirectory + " is not under " + parentDir);
    }
    else {
      path = myDirectory.getPresentableUrl();
    }
    return path;
  }

  @Override
  public String getCheckedDescription() {
    return null;
  }

  @Override
  public String getUncheckedDescription() {
    return ProjectBundle.message("label.directory.will.be.excluded.from.framework.detection", getRelativePath());
  }

  @Override
  public void disableDetection(DetectionExcludesConfiguration configuration) {
    configuration.addExcludedFile(myDirectory, null);
  }
}
