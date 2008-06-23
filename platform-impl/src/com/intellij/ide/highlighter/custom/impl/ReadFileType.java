package com.intellij.ide.highlighter.custom.impl;

import com.intellij.ide.highlighter.custom.SyntaxTable;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.SettingsEditor;
import org.jdom.Element;

public class ReadFileType extends AbstractFileType {

  private final Element myElement;

  public ReadFileType(final SyntaxTable syntaxTable, Element element) {
    super(syntaxTable);
    myElement = element;
  }

  public Element getElement() {
    return myElement;
  }

}
