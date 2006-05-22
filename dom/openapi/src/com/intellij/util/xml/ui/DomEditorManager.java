/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml.ui;

import com.intellij.util.xml.DomElement;

/**
 * @author peter
 */
public interface DomEditorManager {
  void openDomElementEditor(DomElement domElement) ;
  DomElement getCurrentEditedElement();

  void commitCurrentEditor();
}
