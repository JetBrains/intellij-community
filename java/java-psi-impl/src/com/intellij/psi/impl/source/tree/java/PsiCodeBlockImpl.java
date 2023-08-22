// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class PsiCodeBlockImpl extends LazyParseablePsiElement implements PsiCodeBlock {
  private static final Logger LOG = Logger.getInstance(PsiCodeBlockImpl.class);

  public PsiCodeBlockImpl(CharSequence text) {
    super(JavaElementType.CODE_BLOCK, text);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myVariablesSet = null;
    myClassesSet = null;
    myConflict = false;
  }

  @Override
  public PsiStatement @NotNull [] getStatements() {
    return PsiImplUtil.getChildStatements(this);
  }

  @Override
  public int getStatementCount() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    // no lock is needed because all chameleons are expanded already
    int count = 0;
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof PsiStatement) {
        count++;
      }
    }
    return count;
  }

  @Override
  public boolean isEmpty() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      if (child.getPsi() instanceof PsiStatement) {
        return false;
      }
    }
    return true;
  }

  @Override
  public PsiElement getFirstBodyElement() {
    final PsiJavaToken lBrace = getLBrace();
    if (lBrace == null) return null;
    final PsiElement nextSibling = lBrace.getNextSibling();
    return nextSibling == getRBrace() ? null : nextSibling;
  }

  @Override
  public PsiElement getLastBodyElement() {
    final PsiJavaToken rBrace = getRBrace();
    if (rBrace != null) {
      final PsiElement prevSibling = rBrace.getPrevSibling();
      return prevSibling == getLBrace() ? null : prevSibling;
    }
    return getLastChild();
  }

  @Override
  public PsiJavaToken getLBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.LBRACE);
  }

  @Override
  public PsiJavaToken getRBrace() {
    return (PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.RBRACE);
  }

  private volatile Set<String> myVariablesSet;
  private volatile Set<String> myClassesSet;
  private volatile boolean myConflict;

  // return Pair(classes, locals) or null if there was conflict
  @Nullable
  private Couple<Set<String>> buildMaps() {
    Set<String> set1 = myClassesSet;
    Set<String> set2 = myVariablesSet;
    boolean wasConflict = myConflict;
    if (set1 == null || set2 == null) {
      final Set<String> localsSet = new HashSet<>();
      final Set<String> classesSet = new HashSet<>();
      final Ref<Boolean> conflict = new Ref<>(Boolean.FALSE);
      PsiScopesUtil.walkChildrenScopes(this, new PsiScopeProcessor() {
        @Override
        public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
          if (element instanceof PsiLocalVariable || element instanceof PsiPatternVariable) {
            final PsiVariable variable = (PsiVariable)element;
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
      }, PatternResolveState.WHEN_BOTH.putInto(ResolveState.initial()), this, this);

      myClassesSet = set1 = classesSet.isEmpty() ? Collections.emptySet() : classesSet;
      myVariablesSet = set2 = localsSet.isEmpty() ? Collections.emptySet() : localsSet;
      myConflict = wasConflict = conflict.get();
    }
    return wasConflict ? null : Couple.of(set1, set2);
  }

  @Override
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
      while (anchor != null && isNonJavaStatement(anchor)) {
        anchor = anchor.getTreePrev();
        before = Boolean.FALSE;
      }
    }
    else if (before == Boolean.FALSE) {
      while (anchor != null && isNonJavaStatement(anchor)) {
        anchor = anchor.getTreeNext();
        before = Boolean.TRUE;
      }
    }

    return super.addInternal(first, last, anchor, before);
  }

  private static boolean isNonJavaStatement(ASTNode anchor) {
    final PsiElement psi = anchor.getPsi();
    return psi instanceof PsiStatement && psi.getLanguage() != JavaLanguage.INSTANCE;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.LBRACE:
        return findChildByType(JavaTokenType.LBRACE);

      case ChildRole.RBRACE:
        return TreeUtil.findChildBackward(this, JavaTokenType.RBRACE);
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.LBRACE) {
      return getChildRole(child, ChildRole.LBRACE);
    }
    else if (i == JavaTokenType.RBRACE) {
      return getChildRole(child, ChildRole.RBRACE);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCodeBlock(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiCodeBlock";
  }


  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) {
      // Parent element should not see our vars
      return true;
    }
    Couple<Set<String>> pair = buildMaps();
    boolean conflict = pair == null;
    final Set<String> classesSet = conflict ? null : pair.getFirst();
    final Set<String> variablesSet = conflict ? null : pair.getSecond();
    final NameHint hint = processor.getHint(NameHint.KEY);
    if (hint != null && !conflict) {
      final ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
      final String name = hint.getName(state);
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) && classesSet.contains(name)) {
        return walkChildren(processor, state, lastParent, place);
      }
      if ((elementClassHint == null || elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) && variablesSet.contains(name)) {
        return walkChildren(processor, state, lastParent, place);
      }
    }
    else {
      return walkChildren(processor, state, lastParent, place);
    }
    return true;
  }

  private boolean walkChildren(@NotNull PsiScopeProcessor processor,
                               @NotNull ResolveState state,
                               PsiElement lastParent,
                               @NotNull PsiElement place) {
    if (!(getParent() instanceof PsiSwitchBlock)) {
      return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
    }
    PsiElement child = null;
    if (lastParent != null && lastParent.getParent() == this) {
      child = lastParent.getPrevSibling();
      if (child == null) return true; // first element
    }

    if (child == null) {
      child = ((PsiElement)this).getLastChild();
    }

    while (child != null) {
      if (child instanceof PsiSwitchLabelStatementBase) {
        state = PatternResolveState.WHEN_NONE.putInto(state);
      }
      if (!child.processDeclarations(processor, state, null, place)) return false;
      child = child.getPrevSibling();
    }
    return true;
  }

  @Override
  public boolean shouldChangeModificationCount(PsiElement place) {
    PsiElement parent = getParent();
    return !(parent instanceof PsiMethod || parent instanceof PsiClassInitializer);
  }
}
