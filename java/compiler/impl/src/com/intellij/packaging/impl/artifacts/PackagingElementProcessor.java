package com.intellij.packaging.impl.artifacts;

import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementProcessor<E extends PackagingElement<?>> {
  public boolean shouldProcessSubstitution(ComplexPackagingElement<?> element) {
    return true;
  }

  public boolean shouldProcess(PackagingElement<?> element) {
    return true;
  }

  public abstract boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull E e);

  protected static String getPathFromRoot(List<CompositePackagingElement<?>> parents, String separator) {
    StringBuilder builder = new StringBuilder();
    for (int i = parents.size() - 1; i >= 0; i--) {
      builder.append(parents.get(i).getName());
      if (i > 0) {
        builder.append(separator);
      }
    }
    return builder.toString();
  }
}
