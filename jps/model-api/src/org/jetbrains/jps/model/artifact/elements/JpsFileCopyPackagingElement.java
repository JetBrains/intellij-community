package org.jetbrains.jps.model.artifact.elements;

/**
 * @author nik
 */
public interface JpsFileCopyPackagingElement extends JpsPackagingElement {

  String getFilePath();

  void setFilePath(String filePath);

  String getRenamedOutputFileName();

  void setRenamedOutputFileName(String renamedOutputFileName);
}
