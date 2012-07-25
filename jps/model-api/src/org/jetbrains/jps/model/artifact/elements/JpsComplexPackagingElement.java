package org.jetbrains.jps.model.artifact.elements;

import java.util.List;

/**
 * @author nik
 */
public interface JpsComplexPackagingElement extends JpsPackagingElement {
  List<JpsPackagingElement> getSubstitution();
}
