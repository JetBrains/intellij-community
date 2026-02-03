// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PackAndPutIntoDefaultLocationAction extends PutIntoDefaultLocationActionBase {
  public PackAndPutIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final String jarName = suggestJarName();
    final String pathForJars = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.JAR_FILES);
    final Presentation presentation = e.getPresentation();
    if (jarName != null && pathForJars != null) {
      presentation.setText(JavaUiBundle.message("action.text.pack.element.into.0", DeploymentUtil.appendToPath(pathForJars, jarName + ".jar")));
      presentation.setVisible(true);
    }
    else {
      presentation.setVisible(false);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private @Nullable String suggestJarName() {
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    for (PackagingSourceItem item : items) {
      if (item.isProvideElements() && item.getKindOfProducedElements().containsDirectoriesWithClasses()) {
        return item.createPresentation(myArtifactEditor.getContext()).getPresentableName();
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final String pathForJars = myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(PackagingElementOutputKind.JAR_FILES);
    final String jarName = suggestJarName();
    if (pathForJars != null) {
      myArtifactEditor.getLayoutTreeComponent().packInto(mySourceItemsTree.getSelectedItems(), 
                                                         DeploymentUtil.appendToPath(pathForJars, jarName + ".jar"));
    }
  }
}
