package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.JDOMExternalizable;

public interface DeadCodeExtension extends JDOMExternalizable {
  String getDisplayName();
  boolean isEntryPoint(RefElement refElement);
  boolean isSelected();
  void setSelected(boolean selected);
}