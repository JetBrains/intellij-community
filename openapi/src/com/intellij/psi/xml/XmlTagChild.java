package com.intellij.psi.xml;

public interface XmlTagChild extends XmlElement{
  XmlTagChild[] EMPTY_ARRAY = new XmlTagChild[0];
  XmlTag getParentTag();

  XmlTagChild getNextSiblingInTag();
  XmlTagChild getPrevSiblingInTag();
}
