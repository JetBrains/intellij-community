package com.intellij.psi.xml;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

public interface XmlTagValue {
  XmlTagChild[] getChildren();
  XmlText[] getTextElements();
  String getText();
  TextRange getTextRange();

  String getTrimmedText();

  void setText(String value);
}
