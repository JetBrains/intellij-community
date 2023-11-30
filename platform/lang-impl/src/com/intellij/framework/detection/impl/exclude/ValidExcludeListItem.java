// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.exclude;

import com.intellij.framework.FrameworkType;
import com.intellij.ide.presentation.VirtualFilePresentation;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

final class ValidExcludeListItem extends ExcludeListItem {
  private final VirtualFile myFile;
  private final FrameworkType myFrameworkType;

  ValidExcludeListItem(FrameworkType frameworkType, VirtualFile file) {
    myFrameworkType = frameworkType;
    myFile = file;
  }

  @Override
  public String getFrameworkTypeId() {
    return myFrameworkType != null ? myFrameworkType.getId() : null;
  }

  @Override
  public String getFileUrl() {
    return myFile != null ? myFile.getUrl() : null;
  }

  @Override
  public void renderItem(ColoredListCellRenderer renderer) {
    if (myFrameworkType != null) {
      renderer.setIcon(myFrameworkType.getIcon());
      renderer.append(myFrameworkType.getPresentableName());
      if (myFile != null) {
        renderer.append(ProjectBundle.message("framework.detection.in.0", myFile.getName()));
        renderer.append(" (" + myFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
    else {
      renderer.setIcon(VirtualFilePresentation.getIcon(myFile));
      renderer.append(myFile.getName());
      renderer.append(" (" + myFile.getPresentableUrl() + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  @Override
  public String getPresentableFrameworkName() {
    return myFrameworkType != null ? myFrameworkType.getPresentableName() : null;
  }
}
