package org.jetbrains.jps.model.artifact.impl.elements;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.impl.JpsElementBase;

/**
 * @author nik
 */
public abstract class JpsFileCopyPackagingElementBase<Self extends JpsFileCopyPackagingElementBase<Self>> extends JpsElementBase<Self> implements
                                                                                                                                 JpsPackagingElement {
  protected String myFilePath;

  public JpsFileCopyPackagingElementBase(String filePath) {
    myFilePath = filePath;
  }

  @Override
  public void applyChanges(@NotNull Self modified) {
    setFilePath(modified.myFilePath);
  }

  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(String filePath) {
    if (!Comparing.equal(myFilePath, filePath)) {
      myFilePath = filePath;
      fireElementChanged();
    }
  }
}
