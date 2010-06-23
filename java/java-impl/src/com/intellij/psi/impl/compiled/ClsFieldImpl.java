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
package com.intellij.psi.impl.compiled;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Set;

public class ClsFieldImpl extends ClsRepositoryPsiElement<PsiFieldStub> implements PsiField, PsiVariableEx, ClsModifierListOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsFieldImpl");

  private final PsiIdentifier myNameIdentifier;
  private final PsiDocComment myDocComment;
  private PsiTypeElement myType = null;          //guarded by PsiLock.LOCK
  private PsiExpression myInitializer = null;    //guarded by PsiLock.LOCK
  private boolean myInitializerInitialized = false;  //guarded by PsiLock.LOCK

  public ClsFieldImpl(final PsiFieldStub stub) {
    super(stub);
    myDocComment = isDeprecated() ? new ClsDocCommentImpl(this) : null;
    myNameIdentifier = new ClsIdentifierImpl(this, getName());
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiTypeElement type = getTypeElement();
    PsiIdentifier name = getNameIdentifier();

    int count =
      (docComment != null ? 1 : 0)
      + (modifierList != null ? 1 : 0)
      + (type != null ? 1 : 0)
      + (name != null ? 1 : 0);
    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }
    if (modifierList != null) {
      children[offset++] = modifierList;
    }
    if (type != null) {
      children[offset++] = type;
    }
    if (name != null) {
      children[offset++] = name;
    }

    return children;
  }

  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @NotNull
  @NonNls
  public String getName() {
    return getStub().getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @NotNull
  public PsiType getType() {
    return getTypeElement().getType();
  }

  public PsiTypeElement getTypeElement() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myType == null) {
        String typeText = TypeInfo.createTypeText(getStub().getType(false));
        myType = new ClsTypeElementImpl(this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
      }
      return myType;
    }
  }

  public PsiModifierList getModifierList() {
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  public PsiExpression getInitializer() {
    synchronized (LAZY_BUILT_LOCK) {
      if (!myInitializerInitialized) {
        myInitializerInitialized = true;
        String initializerText = getStub().getInitializerText();
        if (initializerText != null && !Comparing.equal(PsiFieldStub.INITIALIZER_TOO_LONG, initializerText)) {
          myInitializer = ClsParsingUtil.createExpressionFromText(initializerText, getManager(), this);
        }
      }

      return myInitializer;
    }
  }

  public boolean hasInitializer() {
    return getInitializer() != null;
  }

  public Object computeConstantValue() {
    return computeConstantValue(new THashSet<PsiVariable>());
  }

  public Object computeConstantValue(Set<PsiVariable> visitedVars) {
    if (!hasModifierProperty(PsiModifier.FINAL)) return null;
    PsiExpression initializer = getInitializer();
    if (initializer == null) return null;

    final String qName = getContainingClass().getQualifiedName();
    if ("java.lang.Float".equals(qName)) {
      @NonNls final String name = getName();
      if ("POSITIVE_INFINITY".equals(name)) return new Float(Float.POSITIVE_INFINITY);
      if ("NEGATIVE_INFINITY".equals(name)) return new Float(Float.NEGATIVE_INFINITY);
      if ("NaN".equals(name)) return new Float(Float.NaN);
    }
    else if ("java.lang.Double".equals(qName)) {
      @NonNls final String name = getName();
      if ("POSITIVE_INFINITY".equals(name)) return new Double(Double.POSITIVE_INFINITY);
      if ("NEGATIVE_INFINITY".equals(name)) return new Double(Double.NEGATIVE_INFINITY);
      if ("NaN".equals(name)) return new Double(Double.NaN);
    }

    return PsiConstantEvaluationHelperImpl.computeCastTo(initializer, getType(), visitedVars);
  }

  public boolean isDeprecated() {
    return getStub().isDeprecated();
  }

  public PsiDocComment getDocComment() {
    return myDocComment;
  }

  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getTypeElement()).appendMirrorText(indentLevel, buffer);
    buffer.append(' ');
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    if (getInitializer() != null) {
      buffer.append(" = ");
      buffer.append(getInitializer().getText());
    }
    buffer.append(';');
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiField mirror = (PsiField)SourceTreeToPsiMap.treeElementToPsi(element);
    if (getDocComment() != null) {
        ((ClsElementImpl)getDocComment()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
      ((ClsElementImpl)getTypeElement()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeElement()));
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitField(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiField:" + getName();
  }

  @NotNull
  public PsiElement getNavigationElement() {
    PsiClass sourceClassMirror = ((ClsClassImpl)getParent()).getSourceMirrorClass();
    PsiElement sourceFieldMirror = sourceClassMirror != null ? sourceClassMirror.findFieldByName(getName(), false) : null;
    return sourceFieldMirror != null ? sourceFieldMirror.getNavigationElement() : this;
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getFieldPresentation(this);
  }
  public void setInitializer(PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public Icon getElementIcon(final int flags) {
    final RowIcon baseIcon = createLayeredIcon(Icons.FIELD_ICON, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isFieldEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }

  public PsiType getTypeNoResolve() {
    return getType(); //todo?
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

}
