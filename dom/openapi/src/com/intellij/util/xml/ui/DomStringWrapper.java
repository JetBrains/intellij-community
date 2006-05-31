/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;

import java.lang.reflect.InvocationTargetException;

/**
 * @author peter
 */
public class DomStringWrapper implements DomWrapper<String>{
  private final GenericDomValue myDomElement;

  public DomStringWrapper(final GenericDomValue domElement) {
    myDomElement = domElement;
  }

  public DomElement getDomElement() {
    return myDomElement;
  }

  public void setValue(final String value) throws IllegalAccessException, InvocationTargetException {
    myDomElement.setStringValue(value);
  }

  public String getValue() throws IllegalAccessException, InvocationTargetException {
    return myDomElement.isValid() ? myDomElement.getStringValue() : null;
  }

  public boolean isValid() {
    return myDomElement.isValid();
  }

  public Project getProject() {
    return myDomElement.getManager().getProject();
  }

  public GlobalSearchScope getResolveScope() {
    return myDomElement.getResolveScope();
  }

  public XmlFile getFile() {
    return myDomElement.getRoot().getFile();
  }

}
