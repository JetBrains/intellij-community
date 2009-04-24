package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.FileCopyPresentation;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author nik
 */
public class FileCopyPackagingElement extends PackagingElement<FileCopyPackagingElement> {
  private String myFilePath;

  public FileCopyPackagingElement() {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
  }

  public FileCopyPackagingElement(String filePath) {
    super(PackagingElementFactoryImpl.FILE_COPY_ELEMENT_TYPE);
    myFilePath = filePath;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new FileCopyPresentation(myFilePath);
  }

  public FileCopyPackagingElement getState() {
    return this;
  }

  public void loadState(FileCopyPackagingElement state) {
    myFilePath = state.getFilePath();
  }

  @Attribute("path")
  public String getFilePath() {
    return myFilePath;
  }

  public void setFilePath(String filePath) {
    myFilePath = filePath;
  }
}
