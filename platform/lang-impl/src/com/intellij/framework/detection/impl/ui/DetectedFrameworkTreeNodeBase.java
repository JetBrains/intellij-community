// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.NlsContexts.Label;

abstract class DetectedFrameworkTreeNodeBase extends CheckedTreeNode {
  protected DetectedFrameworkTreeNodeBase(Object userObject) {
    super(userObject);
    setChecked(true);
  }

  public abstract void renderNode(ColoredTreeCellRenderer renderer);

  public abstract @Nullable @Label String getCheckedDescription();

  public abstract @Nullable @Label String getUncheckedDescription();

  public abstract void disableDetection(DetectionExcludesConfiguration configuration);
}
