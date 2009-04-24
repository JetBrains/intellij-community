package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.DirectoryElementPresentation;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class DirectoryPackagingElement extends CompositePackagingElement<DirectoryPackagingElement> {
  private String myDirectoryName;

  public DirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
  }

  public DirectoryPackagingElement(String directoryName) {
    super(PackagingElementFactoryImpl.DIRECTORY_ELEMENT_TYPE);
    myDirectoryName = directoryName;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new DirectoryElementPresentation(this); 
  }

  public DirectoryPackagingElement getState() {
    return this;
  }

  @Attribute("name")
  public String getDirectoryName() {
    return myDirectoryName;
  }

  public void setDirectoryName(String directoryName) {
    myDirectoryName = directoryName;
  }

  public void loadState(DirectoryPackagingElement state) {
    myDirectoryName = state.getDirectoryName();
  }

  @Override
  public boolean canBeMergedWith(@NotNull PackagingElement<?> element) {
    return element instanceof DirectoryPackagingElement && ((DirectoryPackagingElement)element).getDirectoryName().equals(myDirectoryName);
  }
}
