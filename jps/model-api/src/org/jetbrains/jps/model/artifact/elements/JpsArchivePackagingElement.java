package org.jetbrains.jps.model.artifact.elements;

/**
 * @author nik
 */
public interface JpsArchivePackagingElement extends JpsCompositePackagingElement {
  String getArchiveName();

  void setArchiveName(String directoryName);
}
