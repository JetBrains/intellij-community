/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class AnnotationParamListElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.AnnotationParamListElement");
  private static final TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(JavaElementType.NAME_VALUE_PAIR);

  public AnnotationParamListElement() {
    super(JavaElementType.ANNOTATION_PARAMETER_LIST);
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == JavaTokenType.RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (ElementType.ANNOTATION_MEMBER_VALUE_BIT_SET.contains(i) ||
             (i == JavaElementType.NAME_VALUE_PAIR && child.getFirstChildNode() != null &&
              child.getFirstChildNode().getElementType() == JavaElementType.ANNOTATION_ARRAY_INITIALIZER)) {
      return ChildRole.ANNOTATION_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    switch (role) {
      default:
        LOG.assertTrue(false);
        return null;
      case ChildRole.LPARENTH:
        return findChildByType(JavaTokenType.LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(JavaTokenType.RPARENTH);
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first.getElementType() == JavaElementType.NAME_VALUE_PAIR && last.getElementType() == JavaElementType.NAME_VALUE_PAIR) {
      ASTNode lparenth = findChildByType(JavaTokenType.LPARENTH);
      if (lparenth == null) {
        CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        LeafElement created = Factory.createSingleLeafElement(JavaTokenType.LPARENTH, "(", 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getFirstChildNode(), true);
      }

      ASTNode rparenth = findChildByType(JavaTokenType.RPARENTH);
      if (rparenth == null) {
        CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
        LeafElement created = Factory.createSingleLeafElement(JavaTokenType.RPARENTH, ")", 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getLastChildNode(), false);
      }

      ASTNode[] nodes = getChildren(NAME_VALUE_PAIR_BIT_SET);
      if (nodes.length == 1) {
        ASTNode node = nodes[0];
        if (node instanceof PsiNameValuePair) {
          PsiNameValuePair pair = (PsiNameValuePair)node;
          if (pair.getName() == null) {
            PsiAnnotationMemberValue value = pair.getValue();
            if (value != null) {
              try {
                PsiElementFactory factory = JavaPsiFacade.getInstance(getPsi().getProject()).getElementFactory();
                PsiAnnotation annotation = factory.createAnnotationFromText("@AAA(value = " + value.getText() + ")", null);
                replaceChild(node, annotation.getParameterList().getAttributes()[0].getNode());
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
        }
      }

      if (anchor == null && before != null) {
        anchor = findChildByType(before ? JavaTokenType.RPARENTH : JavaTokenType.LPARENTH);
      }

      TreeElement firstAdded = super.addInternal(first, last, anchor, before);
      JavaSourceUtil.addSeparatingComma(this, first, NAME_VALUE_PAIR_BIT_SET);
      return firstAdded;
    }

    return super.addInternal(first, last, anchor, before);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getElementType() == JavaElementType.NAME_VALUE_PAIR) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }
}
