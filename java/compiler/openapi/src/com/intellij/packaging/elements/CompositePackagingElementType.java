// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.elements;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.function.Supplier;

public abstract class CompositePackagingElementType<E extends CompositePackagingElement<?>> extends PackagingElementType<E> {
  protected CompositePackagingElementType(@NotNull @NonNls String id,
                                          @NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Title) String> presentableName) {
    super(id, presentableName);
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }


  public abstract @Nullable CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent, @Nullable String baseName, @NotNull ArtifactEditorContext context);

  @Override
  public @NotNull @Unmodifiable List<? extends PackagingElement<?>> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact, @NotNull CompositePackagingElement<?> parent) {
    final PackagingElement<?> composite = createComposite(parent, null, context);
    return ContainerUtil.createMaybeSingletonList(composite);
  }
}
