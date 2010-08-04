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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiElementArrayConstructor;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class PsiDeclarationStatementImpl extends CompositePsiElement implements PsiDeclarationStatement {
  public PsiDeclarationStatementImpl() {
    super(JavaElementType.DECLARATION_STATEMENT);
  }

  @NotNull
  public PsiElement[] getDeclaredElements() {
    return getChildrenAsPsiElements(DECLARED_ELEMENT_BIT_SET, PsiElementArrayConstructor.PSI_ELEMENT_ARRAY_CONSTRUCTOR);
  }

  private static final TokenSet DECLARED_ELEMENT_BIT_SET = TokenSet.create(JavaElementType.LOCAL_VARIABLE, JavaElementType.CLASS);

  public int getChildRole(ASTNode child) {
    if (child.getElementType() == JavaTokenType.COMMA) return ChildRole.COMMA;
    return super.getChildRole(child);
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (DECLARED_ELEMENT_BIT_SET.contains(child.getElementType())) {
      PsiElement[] declaredElements = getDeclaredElements();
      int length = declaredElements.length;
      if (length > 0) {
        if (length == 1) {
          getTreeParent().deleteChildInternal(this);
          return;
        } else {
          if (SourceTreeToPsiMap.psiElementToTree(declaredElements[length - 1]) == child) {
            removeCommaBefore(child);
            final LeafElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1,
                                                                          SharedImplUtil.findCharTableByTree(this), getManager());
            SourceTreeToPsiMap.psiElementToTree(declaredElements[length - 2]).addChild(semicolon, null);
          }
          else if (SourceTreeToPsiMap.psiElementToTree(declaredElements[0]) == child) {
            CompositeElement next = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(declaredElements[1]);
            ASTNode copyChild = child.copyElement();
            ASTNode nameChild = ((CompositeElement)copyChild).findChildByRole(ChildRole.NAME);
            removeCommaBefore(next);
            next.addInternal((TreeElement)copyChild.getFirstChildNode(), nameChild.getTreePrev(), null, Boolean.FALSE);
          }
          else {
            removeCommaBefore (child);
          }
        }
      }
    }
    super.deleteChildInternal(child);
  }

  private void removeCommaBefore(ASTNode child) {
    ASTNode prev = child;
    do {
      prev = prev.getTreePrev();
    } while (prev != null && ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(prev.getElementType()));
    if (prev != null && prev.getElementType() == JavaTokenType.COMMA) deleteChildInternal(prev);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDeclarationStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiDeclarationStatement";
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    PsiElement[] decls = getDeclaredElements();
    for (PsiElement decl : decls) {
      if (decl != lastParent) {
        if (!processor.execute(decl, state)) return false;
      }
      else {
        final ElementClassHint hint = processor.getHint(ElementClassHint.KEY);
        if (lastParent instanceof PsiClass) {
          if (hint == null || hint.shouldProcess(ElementClassHint.DeclaractionKind.CLASS)) {
            processor.execute(lastParent, state);
          }
        }
      }
    }

    return true;
  }
}
