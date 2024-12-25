// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.icons.AllIcons;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.packaging.ui.*;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ArtifactsSourceItemsProvider extends PackagingSourceItemsProvider {
  @Override
  public @NotNull Collection<? extends PackagingSourceItem> getSourceItems(@NotNull ArtifactEditorContext editorContext,
                                                                           @NotNull Artifact artifact,
                                                                           @Nullable PackagingSourceItem parent) {
    if (parent == null) {
      if (!ArtifactElementType.getAvailableArtifacts(editorContext, artifact, true).isEmpty()) {
        return Collections.singletonList(new ArtifactsGroupSourceItem());
      }
    }
    else if (parent instanceof ArtifactsGroupSourceItem) {
      List<PackagingSourceItem> items = new ArrayList<>();
      for (Artifact another : ArtifactElementType.getAvailableArtifacts(editorContext, artifact, true)) {
        items.add(new ArtifactSourceItem(another));
      }
      return items;
    }
    return Collections.emptyList();
  }

  private static final class ArtifactsGroupSourceItem extends PackagingSourceItem {
    private ArtifactsGroupSourceItem() {
      super(false);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof ArtifactsGroupSourceItem;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public @NotNull SourceItemPresentation createPresentation(@NotNull ArtifactEditorContext context) {
      return new ArtifactsGroupPresentation();
    }

    @Override
    public @NotNull List<? extends PackagingElement<?>> createElements(@NotNull ArtifactEditorContext context) {
      return Collections.emptyList();
    }

    private static class ArtifactsGroupPresentation extends SourceItemPresentation {
      @Override
      public String getPresentableName() {
        return "Artifacts";
      }

      @Override
      public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                         SimpleTextAttributes commentAttributes) {
        presentationData.setIcon(AllIcons.Nodes.Artifact);
        presentationData.addText(JavaUiBundle.message("display.name.artifacts"), mainAttributes);
      }

      @Override
      public int getWeight() {
        return SourceItemWeights.ARTIFACTS_GROUP_WEIGHT;
      }
    }
  }
}
