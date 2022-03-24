// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.artifacts;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementOutputKind;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.ui.ArtifactProblemsHolder;
import com.intellij.packaging.ui.PackagingSourceItem;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Describes an artifact's type from Project Settings | Artifacts
 * @see Artifact
 * @see ArtifactPropertiesProvider
 */
public abstract class ArtifactType {
  public static final ExtensionPointName<ArtifactType> EP_NAME = new ExtensionPointName<>("com.intellij.packaging.artifactType");
  private final String myId;
  private final Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> myTitle;

  protected ArtifactType(@NonNls String id, Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> title) {
    myId = id;
    myTitle = title;
  }

  public final @NonNls String getId() {
    return myId;
  }

  public @Nls(capitalization = Nls.Capitalization.Sentence) String getPresentableName() {
    return myTitle.get();
  }

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public String getDefaultPathFor(@NotNull PackagingSourceItem sourceItem) {
    return getDefaultPathFor(sourceItem.getKindOfProducedElements());
  }

  @Nullable
  public abstract @NlsSafe String getDefaultPathFor(@NotNull PackagingElementOutputKind kind);

  public boolean isSuitableItem(@NotNull PackagingSourceItem sourceItem) {
    return true;
  }

  public static @NotNull List<ArtifactType> getAllTypes() {
    return EP_NAME.getExtensionList();
  }

  public static @Nullable ArtifactType findById(@NotNull @NonNls String id) {
    for (ArtifactType type : EP_NAME.getIterable()) {
      if (id.equals(type.getId())) {
        return type;
      }
    }
    return null;
  }

  @NotNull
  public abstract CompositePackagingElement<?> createRootElement(@NotNull String artifactName);

  @NotNull
  public List<? extends ArtifactTemplate> getNewArtifactTemplates(@NotNull PackagingElementResolvingContext context) {
    return Collections.emptyList();
  }

  public void checkRootElement(@NotNull CompositePackagingElement<?> rootElement, @NotNull Artifact artifact, @NotNull ArtifactProblemsHolder manager) {
  }

  @Nullable
  public List<? extends PackagingElement<?>> getSubstitution(@NotNull Artifact artifact, @NotNull PackagingElementResolvingContext context,
                                                             @NotNull ArtifactType parentType) {
    return null;
  }
}
