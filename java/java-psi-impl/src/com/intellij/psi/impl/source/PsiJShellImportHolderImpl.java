/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJShellImportHolder;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 * Date: 21-Jun-17
 * according to JShell spec, a snippet must correspond to one of the following JLS syntax productions:
      Expression
      Statement
      ClassDeclaration
      InterfaceDeclaration
      MethodDeclaration
      FieldDeclaration
      ImportDeclaration
 */
public class PsiJShellImportHolderImpl extends ASTWrapperPsiElement implements PsiJShellImportHolder {
  public PsiJShellImportHolderImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    final PsiImportStatement importStatement = getImportStatement();
    if (importStatement != null) {
      processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
      return processor.execute(importStatement, state);
    }
    return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
  }

  @Override
  public PsiImportStatement getImportStatement() {
    return PsiTreeUtil.getChildOfType(this, PsiImportStatement.class);
  }
}
