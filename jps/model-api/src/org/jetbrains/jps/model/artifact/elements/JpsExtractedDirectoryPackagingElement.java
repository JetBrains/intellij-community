package org.jetbrains.jps.model.artifact.elements;

/**
 * @author nik
 */
public interface JpsExtractedDirectoryPackagingElement extends JpsPackagingElement {
  String getFilePath();

  void setFilePath(String path);

  String getPathInJar();

  void setPathInJar(String pathInJar);
}
