package com.intellij.psi.xml;

import com.intellij.util.IncorrectOperationException;

public interface XmlText extends XmlElement, XmlTagChild{
  String getText();
  /**
   * Substituted text
   */
  String getValue();
  void setValue(String s) throws IncorrectOperationException;

  XmlElement insertAtOffset(XmlElement element, int physicalOffset) throws IncorrectOperationException;

  void insertText(String text, int displayOffset) throws IncorrectOperationException;
  void removeText(int displayStart, int displayEnd);

  int physicalToDisplay(int offset);
  int displayToPhysical(int offset);                                             
}
