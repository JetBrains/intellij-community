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

import com.intellij.lang.StdLanguages;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.jsp.IJspElementType;

/**
 * @author peter
 */
public interface JspElementType extends JspTokenType /*extends BaseJspElementType, ELElementType*/ {
  IElementType HOLDER_TEMPLATE_DATA = new IJspElementType("HOLDER_TEMPLATE_DATA");

  IElementType JSP_TEMPLATE_EXPRESSION = new IElementType("JSP_TEMPLATE_EXPRESSION", StdLanguages.JAVA);
  IElementType HOLDER_METHOD = new IJspElementType("HOLDER_METHOD");
  
  IElementType JSP_TEMPLATE_STATEMENT = new IElementType("JSP_TEMPLATE_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_CLASS_LEVEL_DECLARATION_STATEMENT = new IElementType("JSP_CLASS_LEVEL_DECLARATION_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_CODE_BLOCK = new IElementType("JSP_CODE_BLOCK", StdLanguages.JAVA);
  IElementType JSP_WHILE_STATEMENT = new IElementType("JSP_DO_WHILE_STATEMENT", StdLanguages.JAVA);
  IElementType JSP_BLOCK_STATEMENT = new IElementType("JSP_BLOCK_STATEMENT", StdLanguages.JAVA);

  IElementType JSP_CLASS = new IJspElementType("JSP_CLASS");
  IElementType JSP_METHOD_CALL = new IElementType("JSP_METHOD_CALL", StdLanguages.JAVA);
  IElementType JSP_EXPRESSION = JspSpiUtil.getJspExpressionType();
  IElementType JSP_SCRIPTLET = JspSpiUtil.getJspScriptletType();
}
