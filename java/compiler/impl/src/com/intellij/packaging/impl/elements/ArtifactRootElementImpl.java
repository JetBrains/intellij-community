// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.storage.EntitySource;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.MutableEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtensionsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.ArtifactRootElementEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.PackagingElementEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArtifactRootElementImpl extends ArtifactRootElement<Object> {
  public ArtifactRootElementImpl() {
    super(PackagingElementFactoryImpl.ARTIFACT_ROOT_ELEMENT_TYPE);
  }

  @Override
  @NotNull
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new PackagingElementPresentation() {
      @Override
      public @NlsContexts.Label String getPresentableName() {
        return JavaCompilerBundle.message("packaging.element.text.output.root");
      }

      @Override
      public void render(@NotNull PresentationData presentationData, SimpleTextAttributes mainAttributes,
                         SimpleTextAttributes commentAttributes) {
        presentationData.setIcon(AllIcons.Nodes.Artifact);
        presentationData.addText(getPresentableName(), mainAttributes);
      }

      @Override
      public int getWeight() {
        return 0;
      }
    };
  }

  @Override
  public Object getState() {
    return null;
  }

  @Override
  public void loadState(@NotNull Object state) {
  }

  @Override
  public boolean canBeRenamed() {
    return false;
  }

  @Override
  public void rename(@NotNull String newName) {
  }

  @Override
  public String getName() {
    return "";
  }

  @Override
  public String toString() {
    return "<root>";
  }

  @Override
  public WorkspaceEntity getOrAddEntity(@NotNull MutableEntityStorage diff,
                                        @NotNull EntitySource source,
                                        @NotNull Project project) {
    WorkspaceEntity existingEntity = getExistingEntity(diff);
    if (existingEntity != null) return existingEntity;

    List<PackagingElementEntity> children = ContainerUtil.map(this.getChildren(), o -> {
      return (PackagingElementEntity)o.getOrAddEntity(diff, source, project);
    });

    ArtifactRootElementEntity entity = ExtensionsKt.addArtifactRootElementEntity(diff, children, source);
    diff.getMutableExternalMapping("intellij.artifacts.packaging.elements").addMapping(entity, this);
    return entity;
  }
}
