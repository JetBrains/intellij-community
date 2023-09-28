// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.lang.xml.XmlTokenElementMarkTypes;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.TokenSet;


public interface BaseJavaJspElementType {
  ParentAwareTokenSet WHITE_SPACE_BIT_SET = ParentAwareTokenSet.orSet(
    ParentAwareTokenSet.create(TokenSet.WHITE_SPACE),
    ParentAwareTokenSet.create(XmlTokenElementMarkTypes.XML_WHITE_SPACE_MARK));
}
