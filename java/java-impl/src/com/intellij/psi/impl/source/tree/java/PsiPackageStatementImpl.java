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
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.SourceUtil;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

public class PsiPackageStatementImpl extends CompositePsiElement implements PsiPackageStatement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPackageStatementImpl");

  public PsiPackageStatementImpl() {
    super(Constants.PACKAGE_STATEMENT);
  }

  public PsiJavaCodeReferenceElement getPackageReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.PACKAGE_REFERENCE);
  }

  public String getPackageName() {
    PsiJavaCodeReferenceElement ref = getPackageReference();
    return ref == null ? null : SourceUtil.getTextSkipWhiteSpaceAndComments(SourceTreeToPsiMap.psiElementToTree(ref));
  }

  public PsiModifierList getAnnotationList() {
    return (PsiModifierList)findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.PACKAGE_KEYWORD:
        return findChildByType(Constants.PACKAGE_KEYWORD);

      case ChildRole.PACKAGE_REFERENCE:
        return findChildByType(Constants.JAVA_CODE_REFERENCE);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, Constants.SEMICOLON);

      case ChildRole.MODIFIER_LIST:
        return findChildByType(Constants.MODIFIER_LIST);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == Constants.PACKAGE_KEYWORD) {
      return ChildRole.PACKAGE_KEYWORD;
    }
    else if (i == Constants.JAVA_CODE_REFERENCE) {
      return ChildRole.PACKAGE_REFERENCE;
    }
    else if (i == Constants.SEMICOLON) {
      return ChildRole.CLOSING_SEMICOLON;
    }
    else if (i == Constants.MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPackageStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiPackageStatement:" + getPackageName();
  }
}
