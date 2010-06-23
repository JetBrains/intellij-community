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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiMethodStub;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.presentation.java.JavaPresentationUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.ui.RowIcon;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

public class ClsMethodImpl extends ClsRepositoryPsiElement<PsiMethodStub> implements PsiAnnotationMethod {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsMethodImpl");

  private PsiIdentifier myNameIdentifier = null; //protected by PsiLock
  private PsiTypeElement myReturnType = null; //protected by PsiLock
  private PsiDocComment myDocComment = null; //protected by PsiLock
  private PsiAnnotationMemberValue myDefaultValue = null; //protected by PsiLock

  public ClsMethodImpl(final PsiMethodStub stub) {
    super(stub);
  }

  @NotNull
  public PsiElement[] getChildren() {
    PsiDocComment docComment = getDocComment();
    PsiModifierList modifierList = getModifierList();
    PsiTypeElement returnType = getReturnTypeElement();
    PsiIdentifier name = getNameIdentifier();
    PsiParameterList parameterList = getParameterList();
    PsiReferenceList throwsList = getThrowsList();
    PsiAnnotationMemberValue defaultValue = getDefaultValue();

    int count =
      (docComment != null ? 1 : 0)
      + (modifierList != null ? 1 : 0)
      + (returnType != null ? 1 : 0)
      + (name != null ? 1 : 0)
      + (parameterList != null ? 1 : 0)
      + (throwsList != null ? 1 : 0)
      + (defaultValue != null ? 1 : 0);

    PsiElement[] children = new PsiElement[count];

    int offset = 0;
    if (docComment != null) {
      children[offset++] = docComment;
    }
    if (modifierList != null) {
      children[offset++] = modifierList;
    }
    if (returnType != null) {
      children[offset++] = returnType;
    }
    if (name != null) {
      children[offset++] = name;
    }
    if (parameterList != null) {
      children[offset++] = parameterList;
    }
    if (throwsList != null) {
      children[offset++] = throwsList;
    }
    if (defaultValue != null) {
      children[offset++] = defaultValue;
    }

    return children;
  }

  public PsiClass getContainingClass() {
    return (PsiClass)getParent();
  }

  public PsiIdentifier getNameIdentifier() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myNameIdentifier == null) {
        myNameIdentifier = new ClsIdentifierImpl(this, getName());
      }
      return myNameIdentifier;
    }
  }

  @NotNull
  public PsiMethod[] findSuperMethods() {
    return PsiSuperMethodImplUtil.findSuperMethods(this);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, checkAccess);
  }

  @NotNull
  public PsiMethod[] findSuperMethods(PsiClass parentClass) {
    return PsiSuperMethodImplUtil.findSuperMethods(this, parentClass);
  }

  @NotNull
  public List<MethodSignatureBackedByPsiMethod> findSuperMethodSignaturesIncludingStatic(boolean checkAccess) {
    return PsiSuperMethodImplUtil.findSuperMethodSignaturesIncludingStatic(this, checkAccess);
  }

  public PsiMethod findDeepestSuperMethod() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethod(this);
  }

  @NotNull
  public PsiMethod[] findDeepestSuperMethods() {
    return PsiSuperMethodImplUtil.findDeepestSuperMethods(this);
  }

  @NotNull
  public String getName() {
    return getStub().getName();
  }

  @NotNull
  public HierarchicalMethodSignature getHierarchicalMethodSignature() {
    return PsiSuperMethodImplUtil.getHierarchicalMethodSignature(this);
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  public PsiTypeElement getReturnTypeElement() {
    if (isConstructor()) return null;

    synchronized (LAZY_BUILT_LOCK) {
      if (myReturnType == null) {
        String typeText = TypeInfo.createTypeText(getStub().getReturnTypeText(false));
        myReturnType = new ClsTypeElementImpl(this, typeText, ClsTypeElementImpl.VARIANCE_NONE);
      }
      return myReturnType;
    }
  }

  public PsiType getReturnType() {
    PsiTypeElement typeElement = getReturnTypeElement();
    return typeElement == null ? null : typeElement.getType();
  }

  @NotNull
  public PsiModifierList getModifierList() {
    return getStub().findChildStubByType(JavaStubElementTypes.MODIFIER_LIST).getPsi();
  }

  public boolean hasModifierProperty(@NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @NotNull
  public PsiParameterList getParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.PARAMETER_LIST).getPsi();
  }

  @NotNull
  public PsiReferenceList getThrowsList() {
    return getStub().findChildStubByType(JavaStubElementTypes.THROWS_LIST).getPsi();
  }

  public PsiCodeBlock getBody() {
    return null;
  }

  public boolean isDeprecated() {
    return getStub().isDeprecated();
  }

  public PsiAnnotationMemberValue getDefaultValue() {
    synchronized (LAZY_BUILT_LOCK) {
      if (myDefaultValue == null) {
        final String text = getStub().getDefaultValueText();
        if (StringUtil.isEmpty(text)) return null;

        myDefaultValue = ClsAnnotationsUtil.createMemberValueFromText(text, getManager(), this);
      }
      return myDefaultValue;
    }
  }

  public PsiDocComment getDocComment() {
    if (!isDeprecated()) return null;

    synchronized (LAZY_BUILT_LOCK) {
      if (myDocComment == null) {
        myDocComment = new ClsDocCommentImpl(this);
      }
      return myDocComment;
    }
  }


  public boolean isConstructor() {
    return getStub().isConstructor();
  }

  public boolean isVarArgs() {
    return getStub().isVarArgs();
  }

  @NotNull
  public MethodSignature getSignature(@NotNull PsiSubstitutor substitutor) {
    return MethodSignatureBackedByPsiMethod.create(this, substitutor);
  }

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
    appendMethodHeader(buffer, indentLevel);

    if (hasModifierProperty(PsiModifier.ABSTRACT) || hasModifierProperty(PsiModifier.NATIVE)) {
      buffer.append(";");
    }
    else {
      buffer.append(" { /* ");
      buffer.append(PsiBundle.message("psi.decompiled.method.body"));
      buffer.append(" */ }");
    }
  }

  private void appendMethodHeader(@NonNls StringBuffer buffer, final int indentLevel) {
    ClsDocCommentImpl docComment = (ClsDocCommentImpl)getDocComment();
    if (docComment != null) {
      docComment.appendMirrorText(indentLevel, buffer);
      goNextLine(indentLevel, buffer);
    }
    ((ClsElementImpl)getModifierList()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getTypeParameterList()).appendMirrorText(indentLevel, buffer);
    if (!isConstructor()) {
      ((ClsElementImpl)getReturnTypeElement()).appendMirrorText(indentLevel, buffer);
      buffer.append(' ');
    }
    ((ClsElementImpl)getNameIdentifier()).appendMirrorText(indentLevel, buffer);
    ((ClsElementImpl)getParameterList()).appendMirrorText(indentLevel, buffer);
    final PsiReferenceList throwsList = getThrowsList();
    if (throwsList.getReferencedTypes().length > 0) {
      buffer.append(' ');
      ((ClsElementImpl)throwsList).appendMirrorText(indentLevel, buffer);
    }

    PsiAnnotationMemberValue defaultValue = getDefaultValue();
    if (defaultValue != null) {
      buffer.append(" default ");
      ((ClsElementImpl)defaultValue).appendMirrorText(indentLevel, buffer);
    }
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, null);

    PsiMethod mirror = (PsiMethod)SourceTreeToPsiMap.treeElementToPsi(element);
    if (getDocComment() != null) {
        ((ClsElementImpl)getDocComment()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getDocComment()));
    }
      ((ClsElementImpl)getModifierList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getModifierList()));
    if (!isConstructor() && mirror.getReturnTypeElement() != null) {
        ((ClsElementImpl)getReturnTypeElement()).setMirror(
        (TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getReturnTypeElement()));
    }
      ((ClsElementImpl)getNameIdentifier()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getNameIdentifier()));
      ((ClsElementImpl)getParameterList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getParameterList()));
      ((ClsElementImpl)getThrowsList()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getThrowsList()));
      ((ClsElementImpl)getTypeParameterList()).setMirror(
      (TreeElement)SourceTreeToPsiMap.psiElementToTree(mirror.getTypeParameterList()));
    if (getDefaultValue() != null) {
      LOG.assertTrue(mirror instanceof PsiAnnotationMethod);
        ((ClsElementImpl)getDefaultValue()).setMirror((TreeElement)SourceTreeToPsiMap.psiElementToTree(((PsiAnnotationMethod)mirror).getDefaultValue()));
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMethod(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiMethod:" + getName();
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    if (lastParent == null) return true;

    if (!PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place)) return false;

    final PsiParameter[] parameters = getParameterList().getParameters();
    for (PsiParameter parameter : parameters) {
      if (!processor.execute(parameter, state)) return false;
    }

    return true;
  }

  @NotNull
  public PsiElement getNavigationElement() {
    PsiClass sourceClassMirror = ((ClsClassImpl)getParent()).getSourceMirrorClass();
    if (sourceClassMirror == null) return this;
    final PsiMethod[] methodsByName = sourceClassMirror.findMethodsByName(getName(), false);
    for (PsiMethod sourceMethod : methodsByName) {
      if (MethodSignatureUtil.areParametersErasureEqual(this, sourceMethod)) {
        return sourceMethod.getNavigationElement();
      }
    }
    return this;
  }

  public PsiTypeParameterList getTypeParameterList() {
    return getStub().findChildStubByType(JavaStubElementTypes.TYPE_PARAMETER_LIST).getPsi();
  }

  public boolean hasTypeParameters() {
    return PsiImplUtil.hasTypeParameters(this);
  }

  @NotNull public PsiTypeParameter[] getTypeParameters() {
    return PsiImplUtil.getTypeParameters(this);
  }

  public ItemPresentation getPresentation() {
    return JavaPresentationUtil.getMethodPresentation(this);
  }

  public Icon getElementIcon(final int flags) {
    Icon methodIcon = hasModifierProperty(PsiModifier.ABSTRACT) ? Icons.ABSTRACT_METHOD_ICON : Icons.METHOD_ICON;
    RowIcon baseIcon = createLayeredIcon(methodIcon, ElementPresentationUtil.getFlags(this, false));
    return ElementPresentationUtil.addVisibilityIcon(this, flags, baseIcon);
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return PsiClassImplUtil.isMethodEquivalentTo(this, another);
  }

  @NotNull
  public SearchScope getUseScope() {
    return PsiImplUtil.getMemberUseScope(this);
  }
  public PsiMethodReceiver getMethodReceiver() {
    return null; //todo parse cls
  }
  public PsiType getReturnTypeNoResolve() {
    return getReturnType();
  }

  @Override
  protected boolean isVisibilitySupported() {
    return true;
  }

}
