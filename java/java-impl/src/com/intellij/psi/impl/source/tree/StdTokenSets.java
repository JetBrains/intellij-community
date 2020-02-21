// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.jsp.JspTokenType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;

public interface StdTokenSets {
  TokenSet COMMENT_BIT_SET = TokenSet.orSet(
    ElementType.JAVA_COMMENT_BIT_SET, TokenSet.create(JspTokenType.JSP_COMMENT, XmlElementType.XML_COMMENT));

  TokenSet WHITE_SPACE_OR_COMMENT_BIT_SET = TokenSet.orSet(
    JavaJspElementType.WHITE_SPACE_BIT_SET, ElementType.JAVA_COMMENT_BIT_SET);
}
