// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.jlink;

import com.intellij.icons.AllIcons;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JLinkArtifactType extends ArtifactType {
  protected JLinkArtifactType() {
    super("jlink", () -> "Run-time image");
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public @Nullable String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public @NotNull CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArtifactRootElement();
  }
}
