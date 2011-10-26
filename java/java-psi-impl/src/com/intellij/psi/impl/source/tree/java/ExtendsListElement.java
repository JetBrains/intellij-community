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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.lang.ASTNode;

/**
 *  @author dsl
 */
public class ExtendsListElement extends ReferenceListElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.ExtendsListElement");

  public ExtendsListElement() {
    super(JavaElementType.EXTENDS_LIST);
  }

  @Override
  protected String getKeywordText() {
    return PsiKeyword.EXTENDS;
  }

  @Override
  protected IElementType getKeywordType() {
    return JavaTokenType.EXTENDS_KEYWORD;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.EXTENDS_KEYWORD:
        return findChildByType(JavaTokenType.EXTENDS_KEYWORD);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.EXTENDS_KEYWORD) {
      return ChildRole.EXTENDS_KEYWORD;
    }
    else if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.REFERENCE_IN_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }
}
