// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

import java.io.File;

/**
 * @author Roman.Chernyatchik
 */
@ApiStatus.Internal
public final class ProjectViewSelectInExplorerTarget implements DumbAware, SelectInTarget {
  @Override
  public boolean canSelect(SelectInContext context) {
    VirtualFile file = RevealFileAction.findLocalFile(context.getVirtualFile());
    return file != null;
  }

  @Override
  public void selectIn(SelectInContext context, boolean requestFocus) {
    VirtualFile file = RevealFileAction.findLocalFile(context.getVirtualFile());
    if (file != null) {
      RevealFileAction.openFile(new File(file.getPresentableUrl()));
    }
  }

  @Override
  public String toString() {
    return RevealFileAction.getActionName();
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.OS_FILE_MANAGER;
  }
}
