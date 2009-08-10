package com.intellij.packaging.impl.elements;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementType;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class CompositeElementWithClasspath<T> extends CompositePackagingElement<T> {
  private List<String> myClasspath = new ArrayList<String>();

  protected CompositeElementWithClasspath(PackagingElementType type) {
    super(type);
  }

  @Tag("classpath")
  @AbstractCollection(surroundWithTag = false, elementTag = "path")
  public List<String> getClasspath() {
    return myClasspath;
  }

  public void setClasspath(List<String> classpath) {
    myClasspath = classpath;
  }

  public void loadState(T state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
