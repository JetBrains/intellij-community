package org.jetbrains.jps.model.artifact.impl.elements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement;

/**
 * @author nik
 */
public class JpsArchivePackagingElementImpl extends JpsCompositePackagingElementBase<JpsArchivePackagingElementImpl>
  implements JpsArchivePackagingElement {
  private String myArchiveName;

  public JpsArchivePackagingElementImpl(String archiveName) {
    myArchiveName = archiveName;
  }

  private JpsArchivePackagingElementImpl(JpsArchivePackagingElementImpl original) {
    super(original);
    myArchiveName = original.myArchiveName;
  }

  @NotNull
  @Override
  public JpsArchivePackagingElementImpl createCopy() {
    return new JpsArchivePackagingElementImpl(this);
  }

  @Override
  public String getArchiveName() {
    return myArchiveName;
  }

  @Override
  public void setArchiveName(String directoryName) {
    if (!myArchiveName.equals(directoryName)) {
      myArchiveName = directoryName;
      fireElementChanged();
    }
  }
}
