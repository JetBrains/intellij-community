/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ChildRoleBase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class PsiArrayInitializerMemberValueImpl extends PsiCommaSeparatedListImpl implements PsiArrayInitializerMemberValue {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.tree.java.PsiArrayInitializerMemberValueImpl");
  public PsiArrayInitializerMemberValueImpl() {
    super(ANNOTATION_ARRAY_INITIALIZER, ANNOTATION_MEMBER_VALUE_BIT_SET);
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue[] getInitializers() {
    return getChildrenAsPsiElements(ANNOTATION_MEMBER_VALUE_BIT_SET, PsiAnnotationMemberValue.ARRAY_FACTORY);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(LBRACE);

      case ChildRole.RBRACE:
        return findChildByType(RBRACE);
    }
  }

  @Override
  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LBRACE) {
      return ChildRole.LBRACE;
    }
    else if (i == RBRACE) {
      return ChildRole.RBRACE;
    }
    else {
      if (ANNOTATION_MEMBER_VALUE_BIT_SET.contains(child.getElementType())) {
        return ChildRole.ANNOTATION_VALUE;
      }
      return ChildRoleBase.NONE;
    }
  }

  public String toString(){
    return "PsiArrayInitializerMemberValue:" + getText();
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotationArrayInitializer(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
}
