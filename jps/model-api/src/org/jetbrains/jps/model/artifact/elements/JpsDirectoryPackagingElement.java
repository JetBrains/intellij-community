package org.jetbrains.jps.model.artifact.elements;

/**
 * @author nik
 */
public interface JpsDirectoryPackagingElement extends JpsCompositePackagingElement {

  String getDirectoryName();

  void setDirectoryName(String directoryName);
}
