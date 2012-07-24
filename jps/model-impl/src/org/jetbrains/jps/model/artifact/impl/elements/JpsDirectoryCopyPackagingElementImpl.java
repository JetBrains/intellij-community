package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryCopyPackagingElement;

/**
 * @author nik
 */
public class JpsDirectoryCopyPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsDirectoryCopyPackagingElementImpl>
  implements JpsDirectoryCopyPackagingElement {
  public JpsDirectoryCopyPackagingElementImpl(String directoryPath) {
    super(directoryPath);
  }

  @NotNull
  @Override
  public JpsDirectoryCopyPackagingElementImpl createCopy() {
    return new JpsDirectoryCopyPackagingElementImpl(myFilePath);
  }

  @Override
  public String getDirectoryPath() {
    return getFilePath();
  }

  @Override
  public void setDirectoryPath(String path) {
    setFilePath(path);
  }
}
