// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.ArtifactsTreeNode;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.*;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class SourceItemNodeBase extends ArtifactsTreeNode {
  private Artifact myArtifact;
  private final ArtifactEditorEx myArtifactEditor;

  public SourceItemNodeBase(ArtifactEditorContext context, NodeDescriptor parentDescriptor, final TreeNodePresentation presentation,
                            ArtifactEditorEx artifactEditor) {
    super(context, parentDescriptor, presentation);
    myArtifact = artifactEditor.getArtifact();
    myArtifactEditor = artifactEditor;
  }

  protected ArtifactEditorEx getArtifactEditor() {
    return myArtifactEditor;
  }

  @Override
  protected void update(@NotNull PresentationData presentation) {
    final Artifact artifact = myArtifactEditor.getArtifact();
    if (!myArtifact.equals(artifact)) {
      myArtifact = artifact;
    }
    super.update(presentation);
  }

  @Override
  protected SimpleNode[] buildChildren() {
    final PackagingSourceItemsProvider[] providers = PackagingSourceItemsProvider.EP_NAME.getExtensions();
    PackagingSourceItemFilter[] filters = PackagingSourceItemFilter.EP_NAME.getExtensions();
    List<SimpleNode> children = new ArrayList<>();
    for (PackagingSourceItemsProvider provider : providers) {
      final Collection<? extends PackagingSourceItem> items = provider.getSourceItems(myContext, myArtifact, getSourceItem());
      for (PackagingSourceItem item : items) {
        if (myArtifact.getArtifactType().isSuitableItem(item) && isAvailable(item, myContext, filters)) {
          children.add(new SourceItemNode(myContext, this, item, myArtifactEditor));
        }
      }
    }
    return children.isEmpty() ? NO_CHILDREN : children.toArray(new SimpleNode[0]);
  }

  @Contract(pure = true)
  private static boolean isAvailable(@NotNull PackagingSourceItem item, @NotNull ArtifactEditorContext context,
                                     PackagingSourceItemFilter @NotNull [] filters) {
    for (PackagingSourceItemFilter filter : filters) {
      if (!filter.isAvailable(item, context)) {
        return false;
      }
    }
    return true;
  }

  protected abstract @Nullable PackagingSourceItem getSourceItem();
}
