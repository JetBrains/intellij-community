package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryPackagingElement;

/**
 * @author nik
 */
public class JpsDirectoryPackagingElementImpl extends JpsCompositePackagingElementBase<JpsDirectoryPackagingElementImpl> implements JpsDirectoryPackagingElement {
  private String myDirectoryName;

  public JpsDirectoryPackagingElementImpl(String directoryName) {
    myDirectoryName = directoryName;
  }

  private JpsDirectoryPackagingElementImpl(JpsDirectoryPackagingElementImpl original) {
    super(original);
    myDirectoryName = original.myDirectoryName;
  }

  @NotNull
  @Override
  public JpsDirectoryPackagingElementImpl createCopy() {
    return new JpsDirectoryPackagingElementImpl(this);
  }

  @Override
  public String getDirectoryName() {
    return myDirectoryName;
  }

  @Override
  public void setDirectoryName(String directoryName) {
    if (!myDirectoryName.equals(directoryName)) {
      myDirectoryName = directoryName;
      fireElementChanged();
    }
  }
}
