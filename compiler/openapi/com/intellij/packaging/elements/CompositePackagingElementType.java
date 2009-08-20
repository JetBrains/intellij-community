package com.intellij.packaging.elements;

import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Collections;

/**
 * @author nik
 */
public abstract class CompositePackagingElementType<E extends CompositePackagingElement<?>> extends PackagingElementType<E> {
  protected CompositePackagingElementType(@NotNull @NonNls String id, @NotNull String presentableName) {
    super(id, presentableName);
  }

  @Override
  public boolean canCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact) {
    return true;
  }


  @Nullable
  public abstract E createComposite(@NotNull PackagingEditorContext context, CompositePackagingElement<?> parent);

  @NotNull
  public List<? extends E> chooseAndCreate(@NotNull PackagingEditorContext context, @NotNull Artifact artifact, @NotNull CompositePackagingElement<?> parent) {
    final E composite = createComposite(context, parent);
    return composite != null ? Collections.singletonList(composite) : Collections.<E>emptyList();
  }
}
