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
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.jsp.JspContextManager;
import com.intellij.psi.impl.source.jsp.jspJava.JspCodeBlock;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.jsp.BaseJspFile;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.jsp.JspSpiUtil;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.ui.RowIcon;
import com.intellij.util.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class PsiLocalVariableImpl extends CompositePsiElement implements PsiLocalVariable, PsiVariableEx, Constants {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiLocalVariableImpl");

  private volatile String myCachedName = null;

  @SuppressWarnings({"UnusedDeclaration"})
  public PsiLocalVariableImpl() {
    this(LOCAL_VARIABLE);
  }

  protected PsiLocalVariableImpl(final IElementType type) {
    super(type);
  }

  public void clearCaches() {
    super.clearCaches();
    myCachedName = null;
  }

  @NotNull
  public final PsiIdentifier getNameIdentifier() {
    final PsiElement element = findChildByRoleAsPsiElement(ChildRole.NAME);
    assert element instanceof PsiIdentifier : getText();
    return (PsiIdentifier)element;
  }

  @NotNull
  public final String getName() {
    String cachedName = myCachedName;
    if (cachedName == null){
      myCachedName = cachedName = getNameIdentifier().getText();
    }
    return cachedName;
  }

  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public final PsiType getType() {
    return JavaSharedImplUtil.getType(this);
  }

  @NotNull
  public PsiTypeElement getTypeElement() {
    final ASTNode first = getTreeParent().findChildByType(LOCAL_VARIABLE);
    assert first != null : getText();
    final ASTNode type = first.findChildByType(TYPE);
    assert type != null : getText();
    return SourceTreeToPsiMap.treeToPsiNotNull(type);
  }

  public PsiModifierList getModifierList() {
    final CompositeElement parent = getTreeParent();
    if (parent == null) return null;
    final CompositeElement first = (CompositeElement)parent.findChildByType(LOCAL_VARIABLE);
    return first != null ? (PsiModifierList)first.findChildByRoleAsPsiElement(ChildRole.MODIFIER_LIST) : null;
  }

  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  public Object computeConstantValue() {
    return computeConstantValue(new THashSet<PsiVariable>());
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);

    final CompositeElement statement = getTreeParent();
    final PsiElement psiElement = SourceTreeToPsiMap.treeElementToPsi(statement);
    final PsiElement[] variables = psiElement instanceof  PsiDeclarationStatement
                                   ? ((PsiDeclarationStatement)psiElement).getDeclaredElements() : PsiElement.EMPTY_ARRAY;
    if (variables.length > 1) {
      final PsiModifierList modifierList = getModifierList();
      final PsiTypeElement typeElement = getTypeElement();
      assert modifierList != null : getText();
      ASTNode last = statement;
      for (int i = 1; i < variables.length; i++) {
        ASTNode typeCopy = typeElement.copy().getNode();
        ASTNode modifierListCopy = modifierList.copy().getNode();
        CompositeElement variable = (CompositeElement)SourceTreeToPsiMap.psiToTreeNotNull(variables[i]);

        ASTNode comma = TreeUtil.skipElementsBack(variable.getTreePrev(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
        if (comma != null && comma.getElementType() == JavaTokenType.COMMA) {
          CodeEditUtil.removeChildren(statement, comma, variable.getTreePrev());
        }

        CodeEditUtil.removeChild(statement, variable);
        final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(statement);
        CompositeElement statement1 = Factory.createCompositeElement(DECLARATION_STATEMENT, charTableByTree, getManager());
        statement1.addChild(variable, null);

        ASTNode space = Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
        variable.addChild(space, variable.getFirstChildNode());

        variable.addChild(typeCopy, variable.getFirstChildNode());

        if (modifierListCopy.getTextLength() > 0) {
          space = Factory.createSingleLeafElement(JavaTokenType.WHITE_SPACE, " ", 0, 1, treeCharTab, getManager());
          variable.addChild(space, variable.getFirstChildNode());
        }

        variable.addChild(modifierListCopy, variable.getFirstChildNode());

        ASTNode semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, treeCharTab, getManager());
        SourceTreeToPsiMap.psiToTreeNotNull(variables[i - 1]).addChild(semicolon, null);

        CodeEditUtil.addChild(statement.getTreeParent(), statement1, last.getTreeNext());

        last = statement1;
      }
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.INITIALIZER){
      ASTNode eq = findChildByRole(ChildRole.INITIALIZER_EQ);
      if (eq != null){
        deleteChildInternal(eq);
      }
    }
    super.deleteChildInternal(child);
  }

  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.MODIFIER_LIST:
        return findChildByType(MODIFIER_LIST);

      case ChildRole.TYPE:
        return findChildByType(TYPE);

      case ChildRole.NAME:
        return findChildByType(JavaTokenType.IDENTIFIER);

      case ChildRole.INITIALIZER_EQ:
        return findChildByType(JavaTokenType.EQ);

      case ChildRole.INITIALIZER:
        return findChildByType(ElementType.EXPRESSION_BIT_SET);

      case ChildRole.CLOSING_SEMICOLON:
        return TreeUtil.findChildBackward(this, JavaTokenType.SEMICOLON);
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == MODIFIER_LIST) {
      return ChildRole.MODIFIER_LIST;
    }
    else if (i == TYPE) {
      return getChildRole(child, ChildRole.TYPE);
    }
    else if (i == JavaTokenType.IDENTIFIER) {
      return getChildRole(child, ChildRole.NAME);
    }
    else if (i == JavaTokenType.EQ) {
      return getChildRole(child, ChildRole.INITIALIZER_EQ);
    }
    else if (i == JavaTokenType.SEMICOLON) {
      return getChildRole(child, ChildRole.CLOSING_SEMICOLON);
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.INITIALIZER;
      }
      return ChildRoleBase.NONE;
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLocalVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    if (lastParent == null) return true;
    if (lastParent.getContext() instanceof JavaDummyHolder) {
      return processor.execute(this, state);
    }

    if (lastParent.getParent() != this) return true;
    final ASTNode lastParentTree = SourceTreeToPsiMap.psiElementToTree(lastParent);

    return getChildRole(lastParentTree) != ChildRole.INITIALIZER ||
           processor.execute(this, state);
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getVariablePresentation(this);
  }

  public String toString() {
    return "PsiLocalVariable:" + getName();
  }

  @NotNull
  public SearchScope getUseScope() {
    if (JspPsiUtil.isInJspFile(this)) {
      if (getTreeParent().getElementType() == JavaElementType.DECLARATION_STATEMENT &&
          getTreeParent().getTreeParent() instanceof JspCodeBlock &&
          getTreeParent().getTreeParent().getTreeParent().getElementType() == JspElementType.HOLDER_METHOD) { //?
        final JspFile jspFile = JspPsiUtil.getJspFile(this);
        final JspContextManager contextManager = JspContextManager.getInstance(getProject());
        if (contextManager == null) {
          return super.getUseScope();
        }

        final Set<PsiFile> allIncluded = new THashSet<PsiFile>(10);
        final BaseJspFile rootContext = contextManager.getRootContextFile(jspFile);
        allIncluded.add(rootContext);
        JspSpiUtil.visitAllIncludedFilesRecursively(rootContext, new Processor<BaseJspFile>()  {
          public boolean process(final BaseJspFile file) {
            allIncluded.add(file);
            return true;
          }
        });

        return new LocalSearchScope(PsiUtilBase.toPsiFileArray(allIncluded));
      }
    }

    final PsiElement parentElement = getParent();
    if (parentElement instanceof PsiDeclarationStatement) {
      return new LocalSearchScope(parentElement.getParent());
    }
    else {
      return getManager().getFileManager().getUseScope(this);
    }
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(PlatformIcons.VARIABLE_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }
  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

  public PsiType getTypeNoResolve() {
    return getType();
  }
}
