package org.jetbrains.jps.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface JpsReferenceableElement<E extends JpsElement> {
  @NotNull
  JpsElementReference<E> createReference(JpsParentElement parent);
}
