/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.openapi.module.Module;

/**
 * @author peter
*/
public class MockDomFileDescription<T> extends DomFileDescription<T> {
  private final XmlFile myFile;

  public MockDomFileDescription(final Class<T> aClass, final String rootTagName, final XmlFile file) {
    super(aClass, rootTagName);
    myFile = file;
  }

  public boolean isMyFile(final XmlFile xmlFile, final Module module) {
    return myFile == xmlFile;
  }

  public boolean acceptsOtherRootTagNames() {
    return true;
  }

  protected void initializeFileDescription() {
  }

  public boolean isAutomaticHighlightingEnabled() {
    return false;
  }
}
