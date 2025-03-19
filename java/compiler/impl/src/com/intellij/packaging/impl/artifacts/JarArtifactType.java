// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.packaging.artifacts.ArtifactTemplate;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public final class JarArtifactType extends ArtifactType {
  public JarArtifactType() {
    super("jar", JavaCompilerBundle.messagePointer("jar.text"));
  }

  public static JarArtifactType getInstance() {
    return EP_NAME.findExtension(JarArtifactType.class);
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Nodes.Artifact;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElementOutputKind kind) {
    return "/";
  }

  @Override
  public @NotNull CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return PackagingElementFactory.getInstance().createArchive(ArtifactUtil.suggestArtifactFileName(artifactName) + ".jar");
  }

  @Override
  public @NotNull List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    return Collections.singletonList(new JarFromModulesTemplate(context));
  }
}
