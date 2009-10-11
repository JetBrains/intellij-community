/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.jsp;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.jsp.IJspElementType;
import com.intellij.psi.xml.XmlTokenType;

/**
 * @author peter
 */
public interface JspTokenType extends XmlTokenType {
  IElementType JSP_COMMENT = new IJspElementType("JSP_COMMENT");
  IElementType JSP_SCRIPTLET_START = new IJspElementType("JSP_SCRIPTLET_START");
  IElementType JSP_SCRIPTLET_END = new IJspElementType("JSP_SCRIPTLET_END");
  IElementType JSP_DECLARATION_START = new IJspElementType("JSP_DECLARATION_START");
  IElementType JSP_DECLARATION_END = new IJspElementType("JSP_DECLARATION_END");
  IElementType JSP_EXPRESSION_START = new IJspElementType("JSP_EXPRESSION_START");
  IElementType JSP_EXPRESSION_END = new IJspElementType("JSP_EXPRESSION_END");
  IElementType JSP_DIRECTIVE_START = new IJspElementType("JSP_DIRECTIVE_START");
  IElementType JSP_DIRECTIVE_END = new IJspElementType("JSP_DIRECTIVE_END");
  IElementType JSP_BAD_CHARACTER = new IJspElementType("JSP_BAD_CHARACTER");
  IElementType JSP_WHITE_SPACE = new IJspElementType("JSP_WHITE_SPACE"); // for highlighting purposes
  IElementType JAVA_CODE = new IJspElementType("JAVA_CODE");
  IElementType JSP_FRAGMENT = new IJspElementType("JSP_FRAGEMENT"); // passed to template parser for all of jsp code
  IElementType JSPX_ROOT_TAG_HEADER = new IJspElementType("JSPX_ROOT_TAG_HEADER"); // These two only produced by JspxJavaLexer
  IElementType JSPX_ROOT_TAG_FOOTER = new IJspElementType("JSPX_ROOT_TAG_FOOTER");
  IElementType JSPX_JAVA_IN_ATTR_START = new IJspElementType("JSPX_JAVA_IN_ATTR_START");
  IElementType JSPX_JAVA_IN_ATTR_END = new IJspElementType("JSPX_JAVA_IN_ATTR_END");
  IElementType JSPX_JAVA_IN_ATTR = new IJspElementType("JSPX_JAVA_IN_ATTR");

  IElementType JSP_TEMPLATE_DATA = XML_DATA_CHARACTERS;
}
