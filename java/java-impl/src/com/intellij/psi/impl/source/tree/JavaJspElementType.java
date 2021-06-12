// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.jsp.JspTemplateExpressionType;
import com.intellij.psi.tree.TokenSet;


public interface JavaJspElementType {
  TokenSet WHITE_SPACE_BIT_SET = TokenSet.orSet(TokenSet.WHITE_SPACE,
                                                TokenSet.forAllMatching((e) -> e instanceof JspTemplateExpressionType));
}
