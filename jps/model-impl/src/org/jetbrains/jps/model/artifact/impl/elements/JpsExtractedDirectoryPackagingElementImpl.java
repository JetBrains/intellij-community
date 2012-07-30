package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsExtractedDirectoryPackagingElement;

/**
 * @author nik
 */
public class JpsExtractedDirectoryPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsExtractedDirectoryPackagingElementImpl>
  implements JpsExtractedDirectoryPackagingElement {
  private String myPathInJar;

  public JpsExtractedDirectoryPackagingElementImpl(String filePath, String pathInJar) {
    super(filePath);
    myPathInJar = pathInJar;
  }

  @NotNull
  @Override
  public JpsExtractedDirectoryPackagingElementImpl createCopy() {
    return new JpsExtractedDirectoryPackagingElementImpl(myFilePath, myPathInJar);
  }

  @Override
  public void applyChanges(@NotNull JpsExtractedDirectoryPackagingElementImpl modified) {
    super.applyChanges(modified);
    setPathInJar(modified.myPathInJar);
  }

  @Override
  public String getPathInJar() {
    return myPathInJar;
  }

  @Override
  public void setPathInJar(String pathInJar) {
    if (!Comparing.equal(myPathInJar, pathInJar)) {
      myPathInJar = pathInJar;
      fireElementChanged();
    }
  }
}
