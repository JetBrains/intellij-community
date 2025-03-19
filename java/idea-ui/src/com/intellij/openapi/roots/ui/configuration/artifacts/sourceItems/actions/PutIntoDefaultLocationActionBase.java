// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems.SourceItemsTree;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class PutIntoDefaultLocationActionBase extends AnAction {
  protected final SourceItemsTree mySourceItemsTree;
  protected final ArtifactEditorEx myArtifactEditor;

  public PutIntoDefaultLocationActionBase(SourceItemsTree sourceItemsTree, ArtifactEditorEx artifactEditor) {
    mySourceItemsTree = sourceItemsTree;
    myArtifactEditor = artifactEditor;
  }

  protected @Nullable String getDefaultPath(PackagingSourceItem item) {
    return myArtifactEditor.getArtifact().getArtifactType().getDefaultPathFor(item);
  }

  protected static String getTargetLocationText(Set<String> paths) {
    String target;
    if (paths.size() == 1) {
      final String path = StringUtil.trimStart(StringUtil.trimEnd(paths.iterator().next(), "/"), "/");
      if (!path.isEmpty()) {
        target = "/" + path;
      }
      else {
        target = "Output Root";
      }
    }
    else {
      target = "Default Locations";
    }
    return target;
  }
}
