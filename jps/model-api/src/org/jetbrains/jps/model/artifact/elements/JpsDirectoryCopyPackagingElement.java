package org.jetbrains.jps.model.artifact.elements;

/**
 * @author nik
 */
public interface JpsDirectoryCopyPackagingElement extends JpsPackagingElement {
  String getDirectoryPath();

  void setDirectoryPath(String path);
}
