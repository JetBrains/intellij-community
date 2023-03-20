// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PutSourceItemIntoDefaultLocationAction extends PutIntoDefaultLocationActionBase {
  public PutSourceItemIntoDefaultLocationAction(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    super(sourceItemsTree, artifactEditor);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final List<PackagingSourceItem> items = mySourceItemsTree.getSelectedItems();
    boolean enabled = false;
    final Presentation presentation = e.getPresentation();
    if (!items.isEmpty()) {
      enabled = true;
      Set<String> paths = new HashSet<>();
      for (PackagingSourceItem item : items) {
        final String path = getDefaultPath(item);
        if (path == null) {
          enabled = false;
          break;
        }
        paths.add(StringUtil.trimStart(StringUtil.trimEnd(path, "/"), "/"));
      }
      presentation.setText(JavaUiBundle.message("action.text.put.source.item.into.0", getTargetLocationText(paths)));
    }
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    myArtifactEditor.getLayoutTreeComponent().putIntoDefaultLocations(mySourceItemsTree.getSelectedItems());
  }
}
