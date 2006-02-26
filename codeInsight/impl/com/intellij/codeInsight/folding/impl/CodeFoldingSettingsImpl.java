package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.CodeFoldingSettings;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class CodeFoldingSettingsImpl extends CodeFoldingSettings implements NamedJDOMExternalizable {
  public boolean isCollapseImports() {
    return COLLAPSE_IMPORTS;
  }

  public void setCollapseImports(boolean value) {
    COLLAPSE_IMPORTS = value;
  }

  public boolean isCollapseMethods() {
    return COLLAPSE_METHODS;
  }

  public void setCollapseMethods(boolean value) {
    COLLAPSE_METHODS = value;
  }

  public boolean isCollapseAccessors() {
    return COLLAPSE_ACCESSORS;
  }

  public void setCollapseAccessors(boolean value) {
    COLLAPSE_ACCESSORS = value;
  }

  public boolean isCollapseInnerClasses() {
    return COLLAPSE_INNER_CLASSES;
  }

  public void setCollapseInnerClasses(boolean value) {
    COLLAPSE_INNER_CLASSES = value;
  }

  public boolean isCollapseJavadocs() {
    return COLLAPSE_JAVADOCS;
  }

  public void setCollapseJavadocs(boolean value) {
    COLLAPSE_JAVADOCS = value;
  }

  public boolean isCollapseXmlTags() {
    return COLLAPSE_XML_TAGS;
  }

  public void setCollapseXmlTags(boolean value) {
    COLLAPSE_XML_TAGS = value;
  }

  public boolean isCollapseFileHeader() {
    return COLLAPSE_FILE_HEADER;
  }

  public void setCollapseFileHeader(boolean value) {
    COLLAPSE_FILE_HEADER = value;
  }

  public boolean isCollapseAnonymousClasses() {
    return COLLAPSE_ANONYMOUS_CLASSES;
  }

  public void setCollapseAnonymousClasses(boolean value) {
    COLLAPSE_ANONYMOUS_CLASSES = value;
  }


  public boolean isCollapseAnnotations() {
    return COLLAPSE_ANNOTATIONS;
  }

  public void setCollapseAnnotations(boolean value) {
    COLLAPSE_ANNOTATIONS = value;
  }

  public boolean COLLAPSE_IMPORTS = true;
  public boolean COLLAPSE_METHODS = false;
  public boolean COLLAPSE_ACCESSORS = false;
  public boolean COLLAPSE_INNER_CLASSES = false;
  public boolean COLLAPSE_JAVADOCS = false;
  public boolean COLLAPSE_XML_TAGS = false;
  public boolean COLLAPSE_FILE_HEADER = true;
  public boolean COLLAPSE_ANONYMOUS_CLASSES = false;
  public boolean COLLAPSE_ANNOTATIONS = false;

  public String getExternalFileName() {
    return "editor.codeinsight";
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
