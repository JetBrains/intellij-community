// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.ItemPresentationProviders;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stub.JavaStubImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.reference.SoftReference.dereference;

public class PsiFieldImpl extends JavaStubPsiElement<PsiFieldStub> implements PsiField, PsiVariableEx, Queryable {
  private static final Logger LOG = Logger.getInstance(PsiFieldImpl.class);
  private volatile Reference<PsiType> myCachedType;

  public PsiFieldImpl(PsiFieldStub stub) {
    this(stub, JavaStubElementTypes.FIELD);
  }

  protected PsiFieldImpl(PsiFieldStub stub, IElementType type) {
    super(stub, type);
  }

  public PsiFieldImpl(ASTNode node) {
    super(node);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    dropCached();
  }

  private void dropCached() {
    myCachedType = null;
  }

  @Override
  protected Object clone() {
    PsiFieldImpl clone = (PsiFieldImpl)super.clone();
    clone.dropCached();
    return clone;
  }

  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    return parent instanceof PsiClass ? (PsiClass)parent : PsiTreeUtil.getParentOfType(this, PsiSyntheticClass.class);
  }

  @Override
  public PsiElement getContext() {
    PsiClass cc = getContainingClass();
    return cc != null ? cc : super.getContext();
  }

  @Override
  public @NotNull CompositeElement getNode() {
    return (CompositeElement)super.getNode();
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)getNode().findChildByRoleAsPsiElement(ChildRole.NAME);
  }

  @Override
  public @NotNull String getName() {
    PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.getName();
    }
    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException{
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  @SuppressWarnings("Duplicates")
  public @NotNull PsiType getType() {
    PsiFieldStub stub = getStub();
    if (stub != null) {
      PsiType type = dereference(myCachedType);
      if (type == null) {
        type = JavaSharedImplUtil.createTypeFromStub(this, stub.getType());
        myCachedType = new SoftReference<>(type);
      }
      return type;
    }

    myCachedType = null;
    PsiTypeElement typeElement = getTypeElement();
    if (typeElement == null) {
      LOG.error("No type element found for field; children classes = " +
                StringUtil.join(getChildren(), e -> e.getClass().getName(), ", "),
                new Attachment("tree.txt", DebugUtil.psiTreeToString(this, true)));
      return PsiTypes.nullType();
    }
    return JavaSharedImplUtil.getType(typeElement, getNameIdentifier());
  }

  @Override
  public PsiTypeElement getTypeElement(){
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField != this){
      return firstField.getTypeElement();
    }

    return (PsiTypeElement)getNode().findChildByRoleAsPsiElement(ChildRole.TYPE);
  }

  @Override
  public @NotNull PsiModifierList getModifierList() {
    PsiModifierList selfModifierList = getSelfModifierList();
    if (selfModifierList != null) {
      return selfModifierList;
    }
    PsiField firstField = findFirstFieldInDeclaration();
    if (firstField == this) {
      if (!isValid()) throw new PsiInvalidElementAccessException(this);

      PsiField lastResort = findFirstFieldByTree();
      if (lastResort == this) {
        throw new IllegalStateException("Missing modifier list for sequence of fields: '" + getText() + "'");
      }

      firstField = lastResort;
    }

    return firstField.getModifierList();
  }

  private @Nullable PsiModifierList getSelfModifierList() {
    return getStubOrPsiChild(JavaStubElementTypes.MODIFIER_LIST, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  private PsiField findFirstFieldInDeclaration() {
    if (getSelfModifierList() != null) return this;

    PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      List siblings = stub.getParentStub().getChildrenStubs();
      int idx = siblings.indexOf(stub);
      assert idx >= 0;
      for (int i = idx - 1; i >= 0; i--) {
        if (!(siblings.get(i) instanceof PsiFieldStub)) break;
        PsiFieldStub prevField = (PsiFieldStub)siblings.get(i);
        PsiFieldImpl prevFieldPsi = (PsiFieldImpl)prevField.getPsi();
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

  @Override
  public PsiExpression getInitializer() {
    return (PsiExpression)getNode().findChildByRoleAsPsiElement(ChildRole.INITIALIZER);
  }

  /**
   * Avoids stub-to-AST switch if possible.
   * @return Light generated initializer literal expression if it was stored in stubs, the regular initializer otherwise
   */
  public static @Nullable PsiExpression getDetachedInitializer(@NotNull PsiVariable variable) {
    return variable instanceof PsiFieldImpl ? ((PsiFieldImpl)variable).getDetachedInitializer() : variable.getInitializer();
  }

  private @Nullable PsiExpression getDetachedInitializer() {
    PsiFieldStub stub = getGreenStub();
    PsiExpression initializer;
    if (stub == null) {
      initializer = getInitializer();
    }
    else {
      String initializerText = stub.getInitializerText();
      if (StringUtil.isEmpty(initializerText)) {
        return null;
      }

      if (PsiFieldStub.INITIALIZER_NOT_STORED.equals(initializerText) ||
          PsiFieldStub.INITIALIZER_TOO_LONG.equals(initializerText)) {
        initializer = getInitializer();
      }
      else {
        PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
        initializer = parserFacade.createExpressionFromText(initializerText, this);
        ((LightVirtualFile)initializer.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
      }
    }

    return initializer;
  }

  @Override
  public boolean hasInitializer() {
    PsiFieldStub stub = getGreenStub();
    if (stub != null) {
      return stub.getInitializerText() != null;
    }

    return getInitializer() != null;
  }

  @Override
  public Icon getElementIcon(int flags) {
    IconManager iconManager = IconManager.getInstance();
    RowIcon baseIcon =
      iconManager.createLayeredIcon(this, getBaseIcon(), ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  protected @NotNull Icon getBaseIcon() {
    IconManager iconManager = IconManager.getInstance();
    return iconManager.getPlatformIcon(PlatformIcons.Field);
  }

  private static final class OurConstValueComputer implements JavaResolveCache.ConstValueComputer {
    private static final OurConstValueComputer INSTANCE = new OurConstValueComputer();

    @Override
    public Object execute(@NotNull PsiVariable variable, Set<PsiVariable> visitedVars) {
      return ((PsiFieldImpl)variable)._computeConstantValue(visitedVars);
    }
  }

  private @Nullable Object _computeConstantValue(@Nullable Set<PsiVariable> visitedVars) {
    PsiType type = getType();
    // javac rejects all non primitive and non String constants, although JLS states constants "variables whose initializers are constant expressions"
    if (!(type instanceof PsiPrimitiveType) && !type.equalsToText("java.lang.String")) return null;

    PsiExpression initializer = getDetachedInitializer();
    if (initializer == null) return null;
    if (!PsiAugmentProvider.canTrustFieldInitializer(this)) return null;
    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, type, visitedVars);
  }

  @Override
  public Object computeConstantValue() {
    return computeConstantValue(new HashSet<>(2));
  }

  @Override
  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;

    return JavaResolveCache.getInstance(getProject()).computeConstantValueWithCaching(this, OurConstValueComputer.INSTANCE, visitedVars);
  }

  @Override
  public boolean isDeprecated() {
    return JavaStubImplUtil.isMemberDeprecated(this, getGreenStub());
  }

  @Override
  public PsiDocComment getDocComment(){
    PsiFieldStub stub = getGreenStub();
    if (stub != null && !stub.hasDocComment()) return null;

    CompositeElement treeElement = getNode();
    if (getTypeElement() != null) {
      PsiElement element = treeElement.findChildByRoleAsPsiElement(ChildRole.DOC_COMMENT);
      return element instanceof PsiDocComment ? (PsiDocComment)element : null;
    }
    else {
      ASTNode prevField = treeElement.getTreePrev();
      while (prevField != null && prevField.getElementType() != JavaElementType.FIELD){
        prevField = prevField.getTreePrev();
      }
      if (prevField == null) return null;
      return ((PsiField)SourceTreeToPsiMap.treeElementToPsi(prevField)).getDocComment();
    }
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException{
    CheckUtil.checkWritable(this);

    PsiTypeElement type = getTypeElement();
    PsiElement modifierList = getModifierList();
    ASTNode field = SourceTreeToPsiMap.psiElementToTree(type.getParent());
    while(true){
      ASTNode comma = PsiImplUtil.skipWhitespaceAndComments(field.getTreeNext());
      if (comma == null || comma.getElementType() != JavaTokenType.COMMA) break;
      ASTNode nextField = PsiImplUtil.skipWhitespaceAndComments(comma.getTreeNext());
      if (nextField == null || nextField.getElementType() != JavaElementType.FIELD) break;

      TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, getManager());
      CodeEditUtil.addChild(field, semicolon, null);

      CodeEditUtil.removeChild(comma.getTreeParent(), comma);

      PsiElement typeClone = type.copy();
      CodeEditUtil.addChild(nextField, SourceTreeToPsiMap.psiElementToTree(typeClone), nextField.getFirstChildNode());

      PsiElement modifierListClone = modifierList.copy();
      CodeEditUtil.addChild(nextField, SourceTreeToPsiMap.psiElementToTree(modifierListClone), nextField.getFirstChildNode());

      field = nextField;
    }

    JavaSharedImplUtil.normalizeBrackets(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitField(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place){
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @Override
  public String toString() {
    return "PsiField:" + getName();
  }

  @Override
  public PsiElement getOriginalElement() {
    PsiClass containingClass = getContainingClass();
    if (containingClass != null) {
      PsiField originalField = ((PsiClass)containingClass.getOriginalElement()).findFieldByName(getName(), false);
      if (originalField != null) {
        return originalField;
      }
    }
    return this;
  }

  @Override
  public ItemPresentation getPresentation() {
    return ItemPresentationProviders.getItemPresentation(this);
  }

  @Override
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    JavaSharedImplUtil.setInitializer(this, initializer);
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("fieldName", getName());
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }
}