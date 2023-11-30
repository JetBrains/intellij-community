// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.NotNull;

final class FrameworkTypeNode extends DetectedFrameworkTreeNodeBase {
  private final FrameworkType myFrameworkType;

  FrameworkTypeNode(@NotNull FrameworkType frameworkType) {
    super(frameworkType);
    myFrameworkType = frameworkType;
  }

  @Override
  public void renderNode(ColoredTreeCellRenderer renderer) {
    renderer.setIcon(myFrameworkType.getIcon());
    renderer.append(myFrameworkType.getPresentableName());
  }

  @Override
  public String getCheckedDescription() {
    return null;
  }

  @Override
  public String getUncheckedDescription() {
    return ProjectBundle.message("label.framework.detection.will.be.disabled", myFrameworkType.getPresentableName());
  }

  @Override
  public void disableDetection(DetectionExcludesConfiguration configuration) {
    configuration.addExcludedFramework(myFrameworkType);
  }
}
