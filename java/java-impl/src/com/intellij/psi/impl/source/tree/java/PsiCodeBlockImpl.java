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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.jsp.jspJava.JspExpressionStatement;
import com.intellij.psi.impl.source.jsp.jspJava.JspTemplateStatement;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class PsiCodeBlockImpl extends LazyParseablePsiElement implements PsiCodeBlock, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiCodeBlockImpl");

  public PsiCodeBlockImpl(CharSequence text) {
    super(CODE_BLOCK, text);
  }

  public void clearCaches() {
    super.clearCaches();
    myVariablesSet = null;
    myClassesSet = null;
    myConflict = false;
  }

  @NotNull
  public PsiStatement[] getStatements() {
    return getChildrenAsPsiElements(STATEMENT_BIT_SET, PSI_STATEMENT_ARRAY_CONSTRUCTOR);
  }

  public PsiElement getFirstBodyElement() {
    final PsiElement nextSibling = getLBrace().getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  public PsiElement getLastBodyElement() {
    final PsiJavaToken rBrace = getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  private volatile Set<String> myVariablesSet = null;
  private volatile Set<String> myClassesSet = null;
  private volatile boolean myConflict = false;

  // return Pair(classesset, localsSet) or null if there was conflict
  private Pair<Set<String>, Set<String>> buildMaps() {
    Set<String> set1 = myClassesSet;
    Set<String> set2 = myVariablesSet;
    boolean wasConflict = myConflict;
    if (set1 == null || set2 == null) {
      final Set<String> localsSet = new THashSet<String>();
      final Set<String> classesSet = new THashSet<String>();
      final Ref<Boolean> conflict = new Ref<Boolean>(Boolean.FALSE);
      PsiScopesUtil.walkChildrenScopes(this, new BaseScopeProcessor() {
        public boolean execute(PsiElement element, ResolveState state) {
          if (element instanceof PsiLocalVariable) {
            final PsiLocalVariable variable = (PsiLocalVariable)element;
            final String name = variable.getName();
            if (!localsSet.add(name)) {
              conflict.set(Boolean.TRUE);
              localsSet.clear();
              classesSet.clear();
            }
          }
          else if (element instanceof PsiClass) {
            final PsiClass psiClass = (PsiClass)element;
            final String name = psiClass.getName();
            if (!classesSet.add(name)) {
              conflict.set(Boolean.TRUE);
              localsSet.clear();
              classesSet.clear();
            }
          }
          return !conflict.get();
        }
      }, ResolveState.initial(), this, this);

      myClassesSet = set1 = (classesSet.isEmpty() ? Collections.<String>emptySet() : classesSet);
      myVariablesSet = set2 = (localsSet.isEmpty() ? Collections.<String>emptySet() : localsSet);
      myConflict = wasConflict = conflict.get();
    }
    return wasConflict ? null : Pair.create(set1, set2);
  }

  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    if (anchor == null){
      if (before == null || before.booleanValue()){
        anchor = findChildByRole(ChildRole.RBRACE);
        before = Boolean.TRUE;
      }
      else{
        anchor = findChildByRole(ChildRole.LBRACE);
        before = Boolean.FALSE;
      }
    }

    if (before == Boolean.TRUE) {
      while (anchor instanceof JspExpressionStatement || anchor instanceof JspTemplateStatement) {
        anchor = anchor.getTreePrev();
        before = Boolean.FALSE;
      }
    }
    else if (before == Boolean.FALSE) {
      while (anchor instanceof JspExpressionStatement || anchor instanceof JspTemplateStatement) {
        anchor = anchor.getTreeNext();
        before = Boolean.TRUE;
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, RBRACE);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else {
      if (ElementType.STATEMENT_BIT_SET.contains(child.getElementType())) {
        return ChildRole.STATEMENT_IN_BLOCK;
      }
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCodeBlock(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiCodeBlock";
  }


  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) {
      // Parent element should not see our vars
      return true;
    }
    Pair<Set<String>, Set<String>> pair = buildMaps();
    boolean conflict = pair == null;
    final Set<String> classesSet = conflict ? null : pair.getFirst();
    final Set<String> variablesSet = conflict ? null : pair.getSecond();
    final NameHint hint = processor.getHint(NameHint.KEY);
    if (hint != null && !conflict) {
      final ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
      final String name = hint.getName(state);
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) && classesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) && variablesSet.contains(name)) {
        return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
      }
    }
    else {
      return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
    }
    return true;
  }

  public boolean shouldChangeModificationCount(PsiElement place) {
    PsiElement pparent = getParent();
    return !(pparent instanceof PsiMethod || pparent instanceof PsiClassInitializer);
  }
}
