// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.elements;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPropertiesPanel;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.util.List;
import java.util.function.Supplier;

public abstract class PackagingElementType<E extends PackagingElement<?>> {
  public static final ExtensionPointName<PackagingElementType> EP_NAME = ExtensionPointName.create("com.intellij.packaging.elementType");
  private final String myId;
  private final Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> myPresentableName;

  /**
   * @deprecated This constructor is meant to provide the binary compatibility with the external plugins.
   * Please use the constructor that accepts a messagePointer for {@link PackagingElementType#myPresentableName}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected PackagingElementType(@NotNull @NonNls String id, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String presentableName) {
    this(id, () -> presentableName);
  }

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
