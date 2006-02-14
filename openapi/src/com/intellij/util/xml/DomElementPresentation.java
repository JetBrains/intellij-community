package com.intellij.util.xml;

import com.intellij.util.xml.DomElement;
import com.intellij.openapi.util.Key;

import javax.swing.*;

public interface DomElementPresentation<T extends DomElement> {
  String getPresentationName();

  String getTypeName();

  Icon getIcon();
}
