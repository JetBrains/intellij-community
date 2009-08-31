package com.intellij.openapi.roots.ui.configuration.artifacts.sourceItems;

import com.intellij.packaging.ui.*;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ArtifactElementType;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * @author nik
 */
public class ArtifactsSourceItemsProvider extends PackagingSourceItemsProvider {
  @NotNull
  public Collection<? extends PackagingSourceItem> getSourceItems(@NotNull PackagingEditorContext editorContext,
                                                                  @NotNull Artifact artifact,
                                                                  @Nullable PackagingSourceItem parent) {
    if (parent == null) {
      if (!ArtifactElementType.getAvailableArtifacts(editorContext, artifact).isEmpty()) {
        return Collections.singletonList(new ArtifactsGroupSourceItem());
      }
    }
    else if (parent instanceof ArtifactsGroupSourceItem) {
      List<PackagingSourceItem> items = new ArrayList<PackagingSourceItem>();
      for (Artifact another : ArtifactElementType.getAvailableArtifacts(editorContext, artifact)) {
        items.add(new ArtifactSourceItem(another));
      }
      return items;
    }
    return Collections.emptyList();
  }

  private static class ArtifactsGroupSourceItem extends PackagingSourceItem {
    private ArtifactsGroupSourceItem() {
      super(false);
    }

    public boolean equals(Object obj) {
      return obj instanceof ArtifactsGroupSourceItem;
    }

    public int hashCode() {
      return 0;
    }

    public SourceItemPresentation createPresentation(@NotNull PackagingEditorContext context) {
      return new ArtifactsGroupPresentation();
    }

    @NotNull
    public List<? extends PackagingElement<?>> createElements(@NotNull PackagingEditorContext context) {
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
        presentationData.setIcons(PlainArtifactType.ARTIFACT_ICON);
        presentationData.addText("Artifacts", mainAttributes);
      }

      @Override
      public int getWeight() {
        return SourceItemWeights.ARTIFACTS_GROUP_WEIGHT;
      }
    }
  }
}
