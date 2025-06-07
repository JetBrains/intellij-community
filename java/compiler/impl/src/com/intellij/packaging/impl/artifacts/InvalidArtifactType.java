// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@Service
public final class InvalidArtifactType extends ArtifactType {
  public static InvalidArtifactType getInstance() {
    return ApplicationManager.getApplication().getService(InvalidArtifactType.class);
  }

  public InvalidArtifactType() {
    super("invalid", IdeBundle.messagePointer("invalid.node.text"));
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.FileTypes.Unknown;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "";
  }

  @Override
  public @NotNull CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArtifactRootElement();
  }
}
