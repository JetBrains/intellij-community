/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.BlockUtils;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class PsiTryStatementImpl extends CompositePsiElement implements PsiTryStatement, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiTryStatementImpl");

  private volatile PsiParameter[] myCachedCatchParameters;

  public PsiTryStatementImpl() {
    super(TRY_STATEMENT);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedCatchParameters = null;
  }

  @Override
  public PsiCodeBlock getTryBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.TRY_BLOCK);
  }

  @Override
  @NotNull
  public PsiCodeBlock[] getCatchBlocks() {
    ASTNode tryBlock = SourceTreeToPsiMap.psiElementToTree(getTryBlock());
    if (tryBlock != null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiCodeBlock.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      PsiCodeBlock[] blocks = new PsiCodeBlock[lastIncomplete ? catchSections.length - 1 : catchSections.length];
      for (int i = 0; i < blocks.length; i++) {
        blocks[i] = catchSections[i].getCatchBlock();
      }
      return blocks;
    }
    return PsiCodeBlock.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiParameter[] getCatchBlockParameters() {
    PsiParameter[] catchParameters = myCachedCatchParameters;
    if (catchParameters == null) {
      PsiCatchSection[] catchSections = getCatchSections();
      if (catchSections.length == 0) return PsiParameter.EMPTY_ARRAY;
      boolean lastIncomplete = catchSections[catchSections.length - 1].getCatchBlock() == null;
      int limit = lastIncomplete ? catchSections.length - 1 : catchSections.length;
      ArrayList<PsiParameter> parameters = new ArrayList<>();
      for (int i = 0; i < limit; i++) {
        PsiParameter parameter = catchSections[i].getParameter();
        if (parameter != null) parameters.add(parameter);
      }
      myCachedCatchParameters = catchParameters = parameters.toArray(PsiParameter.EMPTY_ARRAY);
    }
    return catchParameters;
  }

  @Override
  @NotNull
  public PsiCatchSection[] getCatchSections() {
    return getChildrenAsPsiElements(CATCH_SECTION_BIT_SET, PsiCatchSection.ARRAY_FACTORY);
  }

  @Override
  public PsiCodeBlock getFinallyBlock() {
    return (PsiCodeBlock)findChildByRoleAsPsiElement(ChildRole.FINALLY_BLOCK);
  }

  @Override
  public PsiResourceList getResourceList() {
    return PsiTreeUtil.getChildOfType(this, PsiResourceList.class);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.TRY_KEYWORD:
        return findChildByType(TRY_KEYWORD);

      case ChildRole.TRY_BLOCK:
        return findChildByType(CODE_BLOCK);

      case ChildRole.FINALLY_KEYWORD:
        return findChildByType(FINALLY_KEYWORD);

      case ChildRole.FINALLY_BLOCK:
        {
          ASTNode finallyKeyword = findChildByRole(ChildRole.FINALLY_KEYWORD);
          if (finallyKeyword == null) return null;
          for(ASTNode child = finallyKeyword.getTreeNext(); child != null; child = child.getTreeNext()){
            if (child.getElementType() == CODE_BLOCK){
              return child;
            }
          }
          return null;
        }
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == TRY_KEYWORD) {
      return ChildRole.TRY_KEYWORD;
    }
    else if (i == FINALLY_KEYWORD) {
      return ChildRole.FINALLY_KEYWORD;
    }
    else if (i == CATCH_SECTION) {
      return ChildRole.CATCH_SECTION;
    }
    else {
      if (child.getElementType() == CODE_BLOCK) {
        int role = getChildRole(child, ChildRole.TRY_BLOCK);
        if (role != ChildRoleBase.NONE) return role;
        return getChildRole(child, ChildRole.FINALLY_BLOCK);
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTryStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull final PsiScopeProcessor processor,
                                     @NotNull final ResolveState state,
                                     final PsiElement lastParent,
                                     @NotNull final PsiElement place) {
    final PsiResourceList resourceList = getResourceList();
    if (resourceList != null && lastParent instanceof PsiCodeBlock && lastParent == getTryBlock()) {
      return PsiImplUtil.processDeclarationsInResourceList(resourceList, processor, state, lastParent);
    }

    return true;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getPsi() instanceof PsiResourceList && getCatchBlocks().length == 0 && getFinallyBlock() == null) {
      final PsiCodeBlock tryBlock = getTryBlock();
      if (tryBlock != null) {
        BlockUtils.unwrapTryBlock(this);
        return;
      }
    }
    super.deleteChildInternal(child);
  }

  public String toString() {
    return "PsiTryStatement";
  }
}
