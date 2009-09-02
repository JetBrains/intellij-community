package com.intellij.packaging.impl.artifacts;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.elements.ComplexPackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementProcessor<E extends PackagingElement<?>> {

  public boolean shouldProcessSubstitution(ComplexPackagingElement<?> element) {
    return true;
  }

  public abstract boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull E e);

  protected final String getPathFromRoot(List<CompositePackagingElement<?>> parents, String separator) {
    return StringUtil.join(parents, new Function<CompositePackagingElement<?>, String>() {
      public String fun(CompositePackagingElement<?> element) {
        return element.getName();
      }
    }, separator);
  }
}
