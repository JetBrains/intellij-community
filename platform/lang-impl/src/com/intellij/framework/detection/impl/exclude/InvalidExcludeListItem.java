// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.exclude;

import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyIcon;

final class InvalidExcludeListItem extends ExcludeListItem {
  private final @NlsSafe String myFileUrl;
  private final String myFrameworkTypeId;

  InvalidExcludeListItem(String frameworkTypeId, @NlsSafe String fileUrl) {
    myFrameworkTypeId = frameworkTypeId;
    myFileUrl = fileUrl;
  }

  @Override
  public String getFrameworkTypeId() {
    return myFrameworkTypeId;
  }

  @Override
  public String getFileUrl() {
    return myFileUrl;
  }

  @Override
  public void renderItem(ColoredListCellRenderer renderer) {
    if (myFrameworkTypeId != null) {
      //noinspection HardCodedStringLiteral
      renderer.append(myFrameworkTypeId, SimpleTextAttributes.ERROR_ATTRIBUTES);
      if (myFileUrl != null) {
        renderer.append(ProjectBundle.message("framework.detection.in.0", myFileUrl), SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
    else {
      renderer.append(myFileUrl, SimpleTextAttributes.ERROR_ATTRIBUTES);
    }
    renderer.setIcon(EmptyIcon.ICON_16);
  }

  @Override
  public String getPresentableFrameworkName() {
    return myFrameworkTypeId;
  }
}
