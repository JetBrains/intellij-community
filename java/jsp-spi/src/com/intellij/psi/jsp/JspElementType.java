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

/**
 * @author peter
 * todo[r.sh] replace usages with JspElementTypeEx in implementation modules
 */
public interface JspElementType {
  enum Kind {
    HOLDER_TEMPLATE_DATA, JSP_TEMPLATE_EXPRESSION, HOLDER_METHOD, JSP_TEMPLATE_STATEMENT, JSP_CLASS_LEVEL_DECLARATION_STATEMENT,
    JSP_CODE_BLOCK, JSP_WHILE_STATEMENT, JSP_BLOCK_STATEMENT, JSP_CLASS, JSP_METHOD_CALL, JSP_EXPRESSION, JSP_SCRIPTLET
  }

  IElementType HOLDER_TEMPLATE_DATA = JspSpiUtil.getJspElementType(Kind.HOLDER_TEMPLATE_DATA);

  IElementType JSP_TEMPLATE_EXPRESSION = JspSpiUtil.getJspElementType(Kind.JSP_TEMPLATE_EXPRESSION);
  IElementType HOLDER_METHOD = JspSpiUtil.getJspElementType(Kind.HOLDER_METHOD);
  
  IElementType JSP_TEMPLATE_STATEMENT = JspSpiUtil.getJspElementType(Kind.JSP_TEMPLATE_STATEMENT);
  IElementType JSP_CLASS_LEVEL_DECLARATION_STATEMENT = JspSpiUtil.getJspElementType(Kind.JSP_CLASS_LEVEL_DECLARATION_STATEMENT);
  IElementType JSP_CODE_BLOCK = JspSpiUtil.getJspElementType(Kind.JSP_CODE_BLOCK);
  IElementType JSP_WHILE_STATEMENT = JspSpiUtil.getJspElementType(Kind.JSP_WHILE_STATEMENT);
  IElementType JSP_BLOCK_STATEMENT = JspSpiUtil.getJspElementType(Kind.JSP_BLOCK_STATEMENT);

  IElementType JSP_CLASS = JspSpiUtil.getJspElementType(Kind.JSP_CLASS);
  IElementType JSP_METHOD_CALL = JspSpiUtil.getJspElementType(Kind.JSP_METHOD_CALL);

  IElementType JSP_EXPRESSION = JspSpiUtil.getJspExpressionType();
  IElementType JSP_SCRIPTLET = JspSpiUtil.getJspScriptletType();
}
