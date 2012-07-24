package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsFileCopyPackagingElement;

/**
 * @author nik
 */
public class JpsFileCopyPackagingElementImpl extends JpsFileCopyPackagingElementBase<JpsFileCopyPackagingElementImpl> implements JpsFileCopyPackagingElement {
  private String myRenamedOutputFileName;

  public JpsFileCopyPackagingElementImpl(String filePath, String renamedOutputFileName) {
    super(filePath);
    myRenamedOutputFileName = renamedOutputFileName;
  }

  @NotNull
  @Override
  public JpsFileCopyPackagingElementImpl createCopy() {
    return new JpsFileCopyPackagingElementImpl(myFilePath, myRenamedOutputFileName);
  }

  @Override
  public void applyChanges(@NotNull JpsFileCopyPackagingElementImpl modified) {
    super.applyChanges(modified);
    setRenamedOutputFileName(modified.myRenamedOutputFileName);
  }

  @Override
  public String getRenamedOutputFileName() {
    return myRenamedOutputFileName;
  }

  @Override
  public void setRenamedOutputFileName(String renamedOutputFileName) {
    if (!Comparing.equal(myRenamedOutputFileName, renamedOutputFileName)) {
      myRenamedOutputFileName = renamedOutputFileName;
      fireElementChanged();
    }
  }
}
