package org.jetbrains.jps.model.artifact.elements;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface JpsCompositePackagingElement extends JpsPackagingElement {
  @NotNull
  List<JpsPackagingElement> getChildren();

  <E extends JpsPackagingElement> E addChild(@NotNull E child);
}
