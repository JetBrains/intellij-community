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
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;

/**
 * @author mike
 */
public interface StdTokenSets {
  TokenSet COMMENT_BIT_SET = TokenSet.orSet(
    ElementType.JAVA_COMMENT_BIT_SET, TokenSet.create(JspElementType.JSP_COMMENT, XmlElementType.XML_COMMENT));

  TokenSet WHITE_SPACE_OR_COMMENT_BIT_SET = TokenSet.orSet(
    ElementType.WHITE_SPACE_BIT_SET, ElementType.JAVA_COMMENT_BIT_SET);
}
