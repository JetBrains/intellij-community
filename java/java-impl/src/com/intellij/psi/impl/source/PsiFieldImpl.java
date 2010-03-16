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
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.InitializerTooLongException;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.parsing.ExpressionParsing;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PatchedSoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PsiFieldImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiField, PsiVariableEx, Queryable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiFieldImpl");

  private volatile PatchedSoftReference<PsiType> myCachedType = null;
  private volatile Object myCachedInitializerValue = null; // PsiExpression on constant value for literal

  public PsiFieldImpl(final PsiFieldStub stub) {
    super(stub, JavaStubElementTypes.FIELD);
  }

  public PsiFieldImpl(final ASTNode node) {
    super(node);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedInitializerValue = null;
    myCachedType = null;
  }

  protected Object clone() {
    PsiFieldImpl clone = (PsiFieldImpl)super.clone();
    clone.dropCached();
    return clone;
  }

  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, JspClass.class);
  }

  @Override
  public PsiElement getContext() {
    final PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @NotNull
  public CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @NotNull
  public String getName() {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiType getTypeNoResolve() {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      String typeText = TypeInfo.createTypeText(stub.getType(false));
      try {
        final PsiType type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    PsiTypeElement typeElement = getTypeElement();
    PsiIdentifier nameIdentifier = getNameIdentifier();
    return JavaSharedImplUtil.getTypeNoResolve(typeElement, nameIdentifier, this);
  }

  @NotNull
  public PsiType getType(){
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      PatchedSoftReference<PsiType> cachedType = myCachedType;
      if (cachedType != null) {
        PsiType type = cachedType.get();
        if (type != null) return type;
      }

      String typeText = TypeInfo.createTypeText(stub.getType(true));
      try {
        final PsiType type = JavaPsiFacade.getInstance(getProject()).getParserFacade().createTypeFromText(typeText, this);
        myCachedType = new PatchedSoftReference<PsiType>(type);
        return type;
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    myCachedType = null;
    return JavaSharedImplUtil.getType(this);
  }

  public PsiTypeElement getTypeElement(){
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField != this){
      return firstField.getTypeElement();
    }

    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @NotNull
  public PsiModifierList getModifierList() {
    final PsiModifierList selfModifierList = getSelfModifierList();
    if (selfModifierList != null) {
      return selfModifierList;
    }
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField == this) {
      if (!isValid()) throw new PsiInvalidElementAccessException(this);

      final PsiField lastResort = findFirstFieldByTree();
      if (lastResort == this) {
        throw new IllegalStateException("Missing modifier list for sequence of fields: '" + getText() + "'");
      }

      firstField = lastResort;
    }

    return firstField.getModifierList();
  }

  @Nullable
  private PsiModifierList getSelfModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST);
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private PsiField findFirstFieldInDeclaration() {
    if (getSelfModifierList() != null) return this;

    final PsiFieldStub stub = getStub();
    if (stub != null) {
      final List siblings = stub.getParentStub().getChildrenStubs();
      final int idx = siblings.indexOf(stub);
      assert idx >= 0;
      for (int i = idx - 1; i >= 0; i--) {
        if (!(siblings.get(i) instanceof PsiFieldStub)) break;
        PsiFieldStub prevField = (PsiFieldStub)siblings.get(i);
        final PsiFieldImpl prevFieldPsi = (PsiFieldImpl)prevField.getPsi();
        if (prevFieldPsi.getSelfModifierList() != null) return prevFieldPsi;
      }
    }

    return findFirstFieldByTree();
  }

  private PsiField findFirstFieldByTree() {
    CompositeElement treeElement = getNode();

    ASTNode modifierList = treeElement.findChildByRole(ChildRole.MODIFIER_LIST);
    if (modifierList == null) {
      ASTNode prevField = treeElement.getTreePrev();
      while (prevField != null && prevField.getElementType() != JavaElementType.FIELD) {
        prevField = prevField.getTreePrev();
      }
      if (prevField == null) return this;
      return ((PsiFieldImpl)SourceTreeToPsiMap.treeElementToPsi(prevField)).findFirstFieldInDeclaration();
    }
    else {
      return this;
    }
  }

  public PsiExpression getInitializer() {
    return (PsiExpression)getNode().findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  public boolean hasInitializer() {
    if (getStub() != null) {
      try {
        return getInitializerText() != null;
      }
      catch (InitializerTooLongException e) {
        return true;
      }
    }

    return getInitializer() != null;
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(Icons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  private static class OurConstValueComputer implements JavaResolveCache.ConstValueComputer {
    private static final OurConstValueComputer INSTANCE = new OurConstValueComputer();

    public Object execute(PsiVariable variable, Set<PsiVariable> visitedVars) {
      return ((PsiFieldImpl)variable)._computeConstantValue(visitedVars);
    }
  }

  private Object _computeConstantValue(Set<PsiVariable> visitedVars) {
    Object cachedInitializerValue = myCachedInitializerValue;
    if (cachedInitializerValue != null && !(cachedInitializerValue instanceof PsiExpression)){
      return cachedInitializerValue;
    }

    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer;
    if (cachedInitializerValue != null) {
      initializer = (PsiExpression)cachedInitializerValue;
    }
    else{
      final PsiFieldStub stub = getStub();
      if (stub == null) {
        initializer = getInitializer();
        if (initializer == null) return null;
      }
      else{
        try{
          String initializerText = getInitializerText();
          if (initializerText == null) return null;

          PsiManager manager = getManager();
          final FileElement holderElement = DummyHolderFactory.createHolder(manager, this).getTreeElement();
          CompositeElement exprElement = ExpressionParsing.parseExpressionText(manager, initializerText, 0, initializerText.length(), holderElement.getCharTable());
          holderElement.rawAddChildren(exprElement);
          initializer = (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprElement);
        }
        catch(InitializerTooLongException e){
          getNode();
          return computeConstantValue(visitedVars);
        }
      }
    }

    Object result = PsiConstantEvaluationHelperImpl.computeCastTo(initializer, type, visitedVars);

    if (initializer instanceof PsiLiteralExpression){
      myCachedInitializerValue = result;
    }
    else{
      myCachedInitializerValue = initializer;
    }


    return result;
  }

  public Object computeConstantValue() {
    Object cachedInitializerValue = myCachedInitializerValue;
    if (cachedInitializerValue != null && !(cachedInitializerValue instanceof PsiExpression)){
      return cachedInitializerValue;
    }

    return computeConstantValue(new HashSet<PsiVariable>(2));
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    return JavaResolveCache.getInstance(getProject()).computeConstantValueWithCaching(this, OurConstValueComputer.INSTANCE, visitedVars);
  }

  @Nullable
  private String getInitializerText() throws InitializerTooLongException {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      return stub.getInitializerText();
    }

    throw new RuntimeException("Shall not be called when in stubless mode");
  }

  public boolean isDeprecated() {
    final PsiFieldStub stub = getStub();
    if (stub != null) {
      return stub.isDeprecated() || stub.hasDeprecatedAnnotation() && PsiImplUtil.isDeprecatedByAnnotation(this);
    }

    return PsiImplUtil.isDeprecatedByDocTag(this) || PsiImplUtil.isDeprecatedByAnnotation(this);
  }

  public PsiDocComment getDocComment(){
    CompositeElement treeElement = getNode();
    if (getTypeElement() != null) {
      return (PsiDocComment)treeElement.findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
    }
    else{
      ASTNode prevField = treeElement.getTreePrev();
      while(prevField.getElementType() != JavaElementType.FIELD){
        prevField = prevField.getTreePrev();
      }
      return ((PsiField)SourceTreeToPsiMap.treeElementToPsi(prevField)).getDocComment();
    }
  }

  public void normalizeDeclaration() throws IncorrectOperationException{
    CheckUtil.checkWritable(this);

    final PsiTypeElement type = getTypeElement();
    PsiElement modifierList = getModifierList();
    ASTNode field = SourceTreeToPsiMap.psiElementToTree(type.getParent());
    while(true){
      ASTNode comma = TreeUtil.skipElements(field.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
      ASTNode nextField = TreeUtil.skipElements(comma.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
      if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;

      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, getManager());
      CodeEditUtil.addChild((CompositeElement)field, semicolon, null);

      CodeEditUtil.removeChild((CompositeElement)comma.getTreeParent(), comma);

      PsiElement typeClone = type.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, SourceTreeToPsiMap.psiElementToTree(typeClone), nextField.getFirstChildNode());

      PsiElement modifierListClone = modifierList.copy();
      CodeEditUtil.addChild((CompositeElement)nextField, SourceTreeToPsiMap.psiElementToTree(modifierListClone), nextField.getFirstChildNode());

      field = nextField;
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitField(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  public String toString(){
    return "PsiField:" + getName();
  }

  public PsiElement getOriginalElement() {
    PsiClass originalClass = (PsiClass)getContainingClass().getOriginalElement();
    PsiField originalField = originalClass.findFieldByName(getName(), false);
    return originalField != null ? originalField : this;
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getFieldPresentation(this);
  }

  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  public void putInfo(Map<String, String> info) {
    info.put("fieldName", getName());
  }
}
