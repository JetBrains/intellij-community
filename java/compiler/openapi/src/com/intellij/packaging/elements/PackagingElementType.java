// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.elements;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

/**
 * Describes an element's type in an artifact's output layout.
 *
 * @see Artifact
 * @see PackagingElementFactory
 * @param <E>
 */
public abstract class PackagingElementType<E extends PackagingElement<?>> {
  public static final ExtensionPointName<PackagingElementType> EP_NAME = ExtensionPointName.create("com.intellij.packaging.elementType");
  private final String myId;
  private final Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> myPresentableName;

  protected PackagingElementType(@NotNull @NonNls String id, @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    myId = id;
    myPresentableName = presentableName;
  }

  public final String getId() {
    return myId;
  }

  public @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName() {
    return myPresentableName.get();
  }

  @Nullable
  public Icon getCreateElementIcon() {
    return null;
  }

  public abstract boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact);

  @NotNull
  public abstract List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact,
                                                                      @NotNull CompositePackagingElement<?> parent);

  @NotNull
  public abstract E createEmpty(@NotNull Project project);

  protected static <T extends PackagingElementType<?>> T getInstance(final Class<T> aClass) {
    for (PackagingElementType type : EP_NAME.getExtensionList()) {
      if (aClass.isInstance(type)) {
        return aClass.cast(type);
      }
    }
    throw new AssertionError();
  }

  @Nullable
  public PackagingElementPropertiesPanel createElementPropertiesPanel(@NotNull E element, @NotNull ArtifactEditorContext context) {
    return null;
  }
}
