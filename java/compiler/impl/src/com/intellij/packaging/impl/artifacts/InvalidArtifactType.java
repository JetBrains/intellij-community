// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Unknown;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "";
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArtifactRootElement();
  }
}
