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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiAnnotationParameterListImpl extends PsiCommaSeparatedListImpl implements PsiAnnotationParameterList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAnnotationParameterListImpl");
  private volatile PsiNameValuePair[] myCachedMembers = null;

  public PsiAnnotationParameterListImpl() {
    super(ANNOTATION_PARAMETER_LIST, NAME_VALUE_PAIR_BIT_SET);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedMembers = null;
  }

  @NotNull
  public PsiNameValuePair[] getAttributes() {
    PsiNameValuePair[] cachedMembers = myCachedMembers;
    if (cachedMembers == null) {
      myCachedMembers = cachedMembers = getChildrenAsPsiElements(NAME_VALUE_PAIR_BIT_SET, PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR);
    }

    return cachedMembers;
  }

  public int getChildRole(ASTNode child) {
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())
             || (i == NAME_VALUE_PAIR && child.getFirstChildNode() != null
                 && child.getFirstChildNode().getElementType() == ANNOTATION_ARRAY_INITIALIZER))
    {
      return ChildRole.ANNOTATION_VALUE;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public ASTNode findChildByRole(int role) {
    switch (role) {
      default:
        LOG.assertTrue(false);
        return null;
      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);
    }
  }

  public String toString() {
    return "PsiAnnotationParameterList";
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationParameterList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (first.getElementType() == NAME_VALUE_PAIR && last.getElementType() == NAME_VALUE_PAIR) {
      final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
      ASTNode lparenth = findChildByRole(ChildRole.LPARENTH);
      if (lparenth == null) {
        LeafElement created = Factory.createSingleLeafElement(LPARENTH, "(", 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getFirstChildNode(), true);
      }
      ASTNode rparenth = findChildByRole(ChildRole.RPARENTH);
      if (rparenth == null) {
        LeafElement created = Factory.createSingleLeafElement(RPARENTH, ")", 0, 1, treeCharTab, getManager());
        super.addInternal(created, created, getLastChildNode(), false);
      }

      final ASTNode[] nodes = getChildren(NAME_VALUE_PAIR_BIT_SET);
      if (nodes.length == 1) {
        final ASTNode node = nodes[0];
        if (node instanceof PsiNameValuePair) {
          final PsiNameValuePair pair = (PsiNameValuePair)node;
          if (pair.getName() == null) {
            final String text = pair.getValue().getText();
            try {
              final PsiAnnotation annotation = JavaPsiFacade.getInstance(getProject()).getElementFactory().createAnnotationFromText("@AAA(value = " + text + ")", null);
              replaceChild(node, annotation.getParameterList().getAttributes()[0].getNode());
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      if (anchor == null && before != null) {
        anchor = findChildByRole(before.booleanValue() ? ChildRole.RPARENTH : ChildRole.LPARENTH);
      }
    }

    return super.addInternal(first, last, anchor, before);
  }
}
