// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.lang.xml.XmlTokenElementMarkTypes;
import com.intellij.psi.impl.source.BasicJavaTokenSet;
import com.intellij.psi.tree.TokenSet;


public interface BaseJavaJspElementType {
  BasicJavaTokenSet WHITE_SPACE_BIT_SET = BasicJavaTokenSet.orSet(
    BasicJavaTokenSet.create(TokenSet.WHITE_SPACE),
          BasicJavaTokenSet.create(XmlTokenElementMarkTypes.XML_WHITE_SPACE_MARK));
}
