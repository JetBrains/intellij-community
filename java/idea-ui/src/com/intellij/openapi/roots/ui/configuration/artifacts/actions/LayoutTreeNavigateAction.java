// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.roots.ui.configuration.artifacts.LayoutTreeComponent;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.ui.TreeNodePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayoutTreeNavigateAction extends ArtifactEditorNavigateActionBase {
  private final LayoutTreeComponent myLayoutTreeComponent;

  public LayoutTreeNavigateAction(LayoutTreeComponent layoutTreeComponent) {
    super(layoutTreeComponent.getLayoutTree());
    myLayoutTreeComponent = layoutTreeComponent;
  }

  @Override
  protected @Nullable TreeNodePresentation getPresentation() {
    PackagingElementNode<?> node = myLayoutTreeComponent.getSelection().getNodeIfSingle();
    return node != null ? node.getElementPresentation() : null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
