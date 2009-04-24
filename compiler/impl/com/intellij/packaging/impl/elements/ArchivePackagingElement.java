package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.ui.ArchiveElementPresentation;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class ArchivePackagingElement extends CompositePackagingElement<ArchivePackagingElement> {
  private String myArchiveFileName;
  private String myMainClass;
  private String myClasspath;

  public ArchivePackagingElement() {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
  }

  public ArchivePackagingElement(String archiveFileName) {
    super(PackagingElementFactoryImpl.ARCHIVE_ELEMENT_TYPE);
    myArchiveFileName = archiveFileName;
  }

  public PackagingElementPresentation createPresentation(PackagingEditorContext context) {
    return new ArchiveElementPresentation(this);
  }

  public ArchivePackagingElement getState() {
    return this;
  }

  public void loadState(ArchivePackagingElement state) {
    myArchiveFileName = state.getArchiveFileName();
    myMainClass = state.getMainClass();
    myClasspath = state.getClasspath();
  }

  @Attribute("name")
  public String getArchiveFileName() {
    return myArchiveFileName;
  }

  public void setArchiveFileName(String archiveFileName) {
    myArchiveFileName = archiveFileName;
  }

  @Tag("main-class")
  public String getMainClass() {
    return myMainClass;
  }

  public void setMainClass(String mainClass) {
    myMainClass = mainClass;
  }

  @Tag("classpath")
  public String getClasspath() {
    return myClasspath;
  }

  public void setClasspath(String classpath) {
    myClasspath = classpath;
  }

  @Override
  public boolean canBeMergedWith(@NotNull PackagingElement<?> element) {
    return element instanceof ArchivePackagingElement && ((ArchivePackagingElement)element).getArchiveFileName().equals(myArchiveFileName);
  }
}
