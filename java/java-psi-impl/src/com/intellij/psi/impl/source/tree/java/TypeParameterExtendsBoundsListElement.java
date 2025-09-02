// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class TypeParameterExtendsBoundsListElement extends ReferenceListElement {
  public TypeParameterExtendsBoundsListElement() {
    super(JavaElementType.EXTENDS_BOUND_LIST, JavaTokenType.EXTENDS_KEYWORD, JavaKeywords.EXTENDS, JavaTokenType.AND, "&");
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    assert child.getTreeParent() == this : child;
    IElementType childType = child.getElementType();
    if (childType == JavaTokenType.AND) return ChildRole.AMPERSAND_IN_BOUNDS_LIST;
    if (childType == JavaElementType.JAVA_CODE_REFERENCE) return ChildRole.BASE_CLASS_REFERENCE;
    return ChildRoleBase.NONE;
  }
}