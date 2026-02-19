// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.elements;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.java.workspace.entities.ArtifactRootElementEntity;
import com.intellij.java.workspace.entities.PackagingElementEntity;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.packaging.elements.ArtifactRootElement;
import com.intellij.packaging.elements.PackagingExternalMapping;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.platform.workspace.storage.EntitySource;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ArtifactRootElementImpl extends ArtifactRootElement<Object> {
  public ArtifactRootElementImpl() {
    super(PackagingElementFactoryImpl.ARTIFACT_ROOT_ELEMENT_TYPE);
  }

  @Override
  public @NotNull PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
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
  public PackagingElementEntity.Builder<? extends PackagingElementEntity> getOrAddEntityBuilder(@NotNull MutableEntityStorage diff,
                                                                                                @NotNull EntitySource source,
                                                                                                @NotNull Project project) {
    PackagingElementEntity existingEntity = (PackagingElementEntity)this.getExistingEntity(diff);
    if (existingEntity != null) return getBuilder(diff, existingEntity);

    List<PackagingElementEntity.Builder<? extends PackagingElementEntity>> children = ContainerUtil.map(this.getChildren(), o -> {
      return o.getOrAddEntityBuilder(diff, source, project);
    });

    ArtifactRootElementEntity entity = diff.addEntity(ArtifactRootElementEntity.create(source, entityBuilder -> {
      entityBuilder.setChildren(children);
      return Unit.INSTANCE;
    }));
    diff.getMutableExternalMapping(PackagingExternalMapping.key).addMapping(entity, this);
    return getBuilder(diff, entity);
  }
}
