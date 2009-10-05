package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class JarArtifactType extends ArtifactType {
  public JarArtifactType() {
    super("jar", "Jar");
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return PlainArtifactType.ARTIFACT_ICON;
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingSourceItem sourceItem) {
    return "/";
  }

  @Override
  public String getDefaultPathFor(@NotNull PackagingElement<?> element, @NotNull PackagingElementResolvingContext context) {
    return "/";
  }

  @NotNull
  @Override
  public CompositePackagingElement<?> createRootElement(@NotNull String artifactName) {
    return new ArchivePackagingElement(artifactName + ".jar");
  }
}
