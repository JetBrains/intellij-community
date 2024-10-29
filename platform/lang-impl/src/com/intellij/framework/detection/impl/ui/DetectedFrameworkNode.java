// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

final class DetectedFrameworkNode extends DetectedFrameworkTreeNodeBase {
  private static final Logger LOG = Logger.getInstance(DetectedFrameworkNode.class);
  private final DetectedFrameworkDescription myDescription;
  private final FrameworkDetectionContext myContext;

  DetectedFrameworkNode(DetectedFrameworkDescription description, FrameworkDetectionContext context) {
    super(description);
    myDescription = description;
    myContext = context;
  }

  @Override
  public void renderNode(ColoredTreeCellRenderer renderer) {
    renderer.setIcon(myDescription.getDetector().getFrameworkType().getIcon());
    final Collection<? extends VirtualFile> files = myDescription.getRelatedFiles();
    final VirtualFile firstFile = ContainerUtil.getFirstItem(files);
    LOG.assertTrue(firstFile != null);
    if (files.size() == 1) {
      renderer.append(firstFile.getName());
      appendDirectoryPath(renderer, firstFile.getParent());
    }
    else {
      String commonName = firstFile.getName();
      VirtualFile commonParent = firstFile.getParent();
      for (VirtualFile file : files) {
        if (commonName != null && !commonName.equals(file.getName())) {
          commonName = null;
        }
        if (commonParent != null && !commonParent.equals(file.getParent())) {
          commonParent = null;
        }
      }
      renderer.append(ProjectBundle.message("comment.0.1.files", files.size(), commonName != null ? commonName : firstFile.getFileType().getDefaultExtension()));
      if (commonParent != null) {
        appendDirectoryPath(renderer, commonParent);
      }
    }
  }

  @Override
  public String getCheckedDescription() {
    return myDescription.getSetupText();
  }

  @Override
  public String getUncheckedDescription() {
    return null;
  }

  @Override
  public void disableDetection(DetectionExcludesConfiguration configuration) {
    Collection<? extends VirtualFile> files = myDescription.getRelatedFiles();
    FrameworkType frameworkType = myDescription.getDetector().getFrameworkType();
    if (files.size() <= 5) {
      for (VirtualFile file : files) {
        configuration.addExcludedFile(file, frameworkType);
      }
    }
    else {
      VirtualFile commonAncestor = VfsUtil.getCommonAncestor(files);
      if (commonAncestor != null) {
        configuration.addExcludedFile(commonAncestor, frameworkType);
      }
      else {
        LOG.info("Cannot find common ancestor for " + files.size() + " files, disabling detection for " + frameworkType.getId() + " in the whole project");
        configuration.addExcludedFramework(frameworkType);
      }
    }
  }

  private void appendDirectoryPath(ColoredTreeCellRenderer renderer, final VirtualFile dir) {
    final String path = getRelativePath(dir);
    renderer.append(" (" + (path.isEmpty() ? "/" : path) + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  private @NotNull @NlsSafe String getRelativePath(@NotNull VirtualFile file) {
    final VirtualFile dir = myContext.getBaseDir();
    if (dir != null) {
      final String path = VfsUtilCore.getRelativePath(dir, file, File.separatorChar);
      if (path != null) {
        return path;
      }
    }
    return file.getPresentableUrl();
  }
}
