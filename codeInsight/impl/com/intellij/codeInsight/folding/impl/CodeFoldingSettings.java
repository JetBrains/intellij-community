package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class CodeFoldingSettings implements NamedJDOMExternalizable, ApplicationComponent{

  public static CodeFoldingSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(CodeFoldingSettings.class);
  }

  public boolean COLLAPSE_IMPORTS = true;
  public boolean COLLAPSE_METHODS = false;
  public boolean COLLAPSE_ACCESSORS = false;
  public boolean COLLAPSE_INNER_CLASSES = false;
  public boolean COLLAPSE_JAVADOCS = false;
  public boolean COLLAPSE_XML_TAGS = false;
  public boolean COLLAPSE_FILE_HEADER = true;
  public boolean COLLAPSE_ANONYMOUS_CLASSES = false;

  public String getComponentName() {
    return "CodeFoldingSettings";
  }

  public String getExternalFileName() {
    return "editor.codeinsight";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
