package com.intellij.packaging.elements;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.ui.ArtifactEditorContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public abstract class CompositePackagingElementType<E extends CompositePackagingElement<?>> extends PackagingElementType<E> {
  protected CompositePackagingElementType(@NotNull @NonNls String id, @NotNull String presentableName) {
    super(id, presentableName);
  }

  @Override
  public boolean canCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact) {
    return true;
  }


  @Nullable
  public abstract E createComposite(@NotNull ArtifactEditorContext context, CompositePackagingElement<?> parent);

  @NotNull
  public List<? extends E> chooseAndCreate(@NotNull ArtifactEditorContext context, @NotNull Artifact artifact, @NotNull CompositePackagingElement<?> parent) {
    final E composite = createComposite(context, parent);
    return composite != null ? Collections.singletonList(composite) : Collections.<E>emptyList();
  }
}
