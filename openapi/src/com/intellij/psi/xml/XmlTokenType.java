/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.xml.IXmlElementType;

public interface XmlTokenType {
  IElementType XML_START_TAG_START = new IXmlElementType("XML_START_TAG_START");
  IElementType XML_END_TAG_START = new IXmlElementType("XML_END_TAG_START");
  IElementType XML_TAG_END = new IXmlElementType("XML_TAG_END");
  IElementType XML_EMPTY_ELEMENT_END = new IXmlElementType("XML_EMPTY_ELEMENT_END");
  IElementType XML_TAG_NAME = new IXmlElementType("XML_TAG_NAME");
  IElementType XML_NAME = new IXmlElementType("XML_NAME");
  IElementType XML_ATTRIBUTE_VALUE_TOKEN = new IXmlElementType("XML_ATTRIBUTE_VALUE_TOKEN");
  IElementType XML_ATTRIBUTE_VALUE_START_DELIMITER = new IXmlElementType("XML_ATTRIBUTE_VALUE_START_DELIMITER");
  IElementType XML_ATTRIBUTE_VALUE_END_DELIMITER = new IXmlElementType("XML_ATTRIBUTE_VALUE_END_DELIMITER");
  IElementType XML_EQ = new IXmlElementType("XML_EQ");
  IElementType XML_DATA_CHARACTERS = new IXmlElementType("XML_DATA_CHARACTERS");
  IElementType XML_WHITE_SPACE = JavaTokenType.WHITE_SPACE;
  IElementType XML_REAL_WHITE_SPACE = new IXmlElementType("XML_WHITE_SPACE");
  IElementType XML_COMMENT_START = new IXmlElementType("XML_COMMENT_START");
  IElementType XML_COMMENT_END = new IXmlElementType("XML_COMMENT_END");
  IElementType XML_COMMENT_CHARACTERS = new IXmlElementType("XML_COMMENT_CHARACTERS");

  IElementType XML_DECL_START = new IXmlElementType("XML_DECL_START");
  IElementType XML_DECL_END = new IXmlElementType("XML_DECL_END");

  IElementType XML_DOCTYPE_START = new IXmlElementType("XML_DOCTYPE_START");
  IElementType XML_DOCTYPE_END = new IXmlElementType("XML_DOCTYPE_END");
  IElementType XML_DOCTYPE_SYSTEM = new IXmlElementType("XML_DOCTYPE_SYSTEM");
  IElementType XML_DOCTYPE_PUBLIC = new IXmlElementType("XML_DOCTYPE_PUBLIC");

  IElementType XML_MARKUP_START = new IXmlElementType("XML_MARKUP_START");
  IElementType XML_MARKUP_END = new IXmlElementType("XML_MARKUP_END");

  IElementType XML_CDATA_START = new IXmlElementType("XML_CDATA_START");
  IElementType XML_CDATA_END = new IXmlElementType("XML_CDATA_END");

  IElementType XML_ELEMENT_DECL_START = new IXmlElementType("XML_ELEMENT_DECL_START");
  IElementType XML_NOTATION_DECL_START = new IXmlElementType("XML_NOTATION_DECL_START");
  IElementType XML_ATTLIST_DECL_START = new IXmlElementType("XML_ATTLIST_DECL_START");
  IElementType XML_ENTITY_DECL_START = new IXmlElementType("XML_ENTITY_DECL_START");

  IElementType XML_PCDATA = new IXmlElementType("XML_PCDATA");
  IElementType XML_LEFT_PAREN = new IXmlElementType("XML_LEFT_PAREN");
  IElementType XML_RIGHT_PAREN = new IXmlElementType("XML_RIGHT_PAREN");
  IElementType XML_CONTENT_EMPTY = new IXmlElementType("XML_CONTENT_EMPTY");
  IElementType XML_CONTENT_ANY = new IXmlElementType("XML_CONTENT_ANY");
  IElementType XML_QUESTION = new IXmlElementType("XML_QUESTION");
  IElementType XML_STAR = new IXmlElementType("XML_STAR");
  IElementType XML_PLUS = new IXmlElementType("XML_PLUS");
  IElementType XML_BAR = new IXmlElementType("XML_BAR");
  IElementType XML_COMMA = new IXmlElementType("XML_COMMA");
  IElementType XML_AMP = new IXmlElementType("XML_AMP");
  IElementType XML_SEMI = new IXmlElementType("XML_SEMI");
  IElementType XML_PERCENT = new IXmlElementType("XML_PERCENT");

  IElementType XML_ATT_IMPLIED = new IXmlElementType("XML_ATT_IMPLIED");
  IElementType XML_ATT_REQUIRED = new IXmlElementType("XML_ATT_REQUIRED");
  IElementType XML_ATT_FIXED = new IXmlElementType("XML_ATT_FIXED");

  IElementType XML_ENTITY_REF_TOKEN = new IXmlElementType("XML_ENTITY_REF_TOKEN");

  IElementType TAG_WHITE_SPACE = new IXmlElementType("TAG_WHITE_SPACE");

  IElementType XML_PI_START = new IXmlElementType("XML_PI_START");
  IElementType XML_PI_END = new IXmlElementType("XML_PI_END");
  IElementType XML_PI_TARGET = new IXmlElementType("XML_PI_TARGET");

  IElementType XML_CHAR_ENTITY_REF = new IXmlElementType("XML_CHAR_ENTITY_REF");

  IElementType XML_BAD_CHARACTER = new IXmlElementType("XML_BAD_CHARACTER");
  IElementType XML_MARKUP = XmlElementType.XML_MARKUP_DECL; //chameleon
  IElementType XML_EMBEDDED_CHAMELEON = XmlElementType.XML_EMBEDDED_CHAMELEON; //chameleon
}
