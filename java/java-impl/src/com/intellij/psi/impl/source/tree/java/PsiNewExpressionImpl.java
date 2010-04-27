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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiNewExpressionImpl extends ExpressionPsiElement implements PsiNewExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl");

  public PsiNewExpressionImpl() {
    super(JavaElementType.NEW_EXPRESSION);
  }

  public PsiType getType(){
    PsiType type = null;
    List<PsiAnnotation> annotations = new SmartList<PsiAnnotation>();
    for(ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()){
      IElementType elementType = child.getElementType();
      if (elementType == JavaElementType.ANNOTATION) {
        annotations.add((PsiAnnotation)child.getPsi());
        continue;
      }
      if (elementType == JavaElementType.JAVA_CODE_REFERENCE){
        LOG.assertTrue(type == null);
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(child), null);
      }
      else if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(elementType)){
        LOG.assertTrue(type == null);
        PsiAnnotation[] annos = annotations.toArray(new PsiAnnotation[annotations.size()]);
        type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(child.getText(), annos);
      }
      else if (elementType == JavaTokenType.LBRACKET){
        LOG.assertTrue(type != null);
        PsiAnnotation[] annos = annotations.toArray(new PsiAnnotation[annotations.size()]);

        type = type.createArrayType(annos);
      }
      else if (elementType == JavaElementType.ANONYMOUS_CLASS){
        PsiAnnotation[] annos = annotations.toArray(new PsiAnnotation[annotations.size()]);
        PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        PsiClass aClass = (PsiClass)SourceTreeToPsiMap.treeElementToPsi(child);
        PsiSubstitutor substitutor = aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : factory.createRawSubstitutor(aClass);
        type = factory.createType(aClass, substitutor, PsiUtil.getLanguageLevel(aClass),annos);
      }
    }
    return type;
  }

  public PsiExpressionList getArgumentList() {
    PsiExpressionList list = (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    if (list != null) return list;
    CompositeElement anonymousClass = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(findChildByRoleAsPsiElement(ChildRole.ANONYMOUS_CLASS));
    if (anonymousClass != null){
      return (PsiExpressionList)anonymousClass.findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    }
    return null;
  }

  @NotNull
  public PsiExpression[] getArrayDimensions() {
    PsiExpression[] expressions = getChildrenAsPsiElements(ElementType.ARRAY_DIMENSION_BIT_SET, Constants.PSI_EXPRESSION_ARRAY_CONSTRUCTOR);
    PsiExpression qualifier = getQualifier();
    if (qualifier == null){
      return expressions;
    }
    else{
      LOG.assertTrue(expressions[0] == qualifier);
      PsiExpression[] expressions1 = new PsiExpression[expressions.length - 1];
      System.arraycopy(expressions, 1, expressions1, 0, expressions1.length);
      return expressions1;
    }
  }

  public PsiArrayInitializerExpression getArrayInitializer() {
    return (PsiArrayInitializerExpression)findChildByRoleAsPsiElement(ChildRole.ARRAY_INITIALIZER);
  }

  public PsiMethod resolveMethod() {
    return resolveConstructor();
  }

  private PsiPolyVariantCachingReference getConstructorFakeReference() {
    return new PsiPolyVariantCachingReference() {
      @NotNull
      public JavaResolveResult[] resolveInner(boolean incompleteCode) {
        ASTNode classRef = findChildByRole(ChildRole.TYPE_REFERENCE);
        if (classRef != null) {
          ASTNode argumentList = TreeUtil.skipElements(classRef.getTreeNext(), StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET);
          if (argumentList != null && argumentList.getElementType() == JavaElementType.EXPRESSION_LIST) {
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
            PsiType aClass = facade.getElementFactory().createType((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(classRef));
            return facade.getResolveHelper().multiResolveConstructor((PsiClassType)aClass,
                                                                      (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                      PsiNewExpressionImpl.this);
          }
        }
        else{
          ASTNode anonymousClassElement = findChildByType(JavaElementType.ANONYMOUS_CLASS);
          if (anonymousClassElement != null) {
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
            final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClassElement);
            PsiType aClass = anonymousClass.getBaseClassType();
            ASTNode argumentList = anonymousClassElement.findChildByType(JavaElementType.EXPRESSION_LIST);
            return facade.getResolveHelper().multiResolveConstructor((PsiClassType)aClass,
                                                                      (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                      anonymousClass);
          }
        }
        return JavaResolveResult.EMPTY_ARRAY;
      }

      public PsiElement getElement() {
        return PsiNewExpressionImpl.this;
      }

      public TextRange getRangeInElement() {
        return null;
      }

      @NotNull
      public String getCanonicalText() {
        return null;
      }

      public PsiElement handleElementRename(String newElementName) {
        return null;
      }

      public PsiElement bindToElement(@NotNull PsiElement element) {
        return null;
      }

      @NotNull
      public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      @Override
      public int hashCode() {
        PsiJavaCodeReferenceElement ref = getClassOrAnonymousClassReference();
        return ref == null ? 0 : ref.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof PsiPolyVariantCachingReference && getElement() == ((PsiReference)obj).getElement();
      }
    };
  }

  @NotNull
  public JavaResolveResult resolveMethodGenerics() {
    ResolveResult[] results = getConstructorFakeReference().multiResolve(false);
    return results.length == 1 ? (JavaResolveResult)results[0] : JavaResolveResult.EMPTY;
  }

  public PsiExpression getQualifier() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @NotNull
  public PsiReferenceParameterList getTypeArgumentList() {
    return (PsiReferenceParameterList) findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  @NotNull
  public PsiType[] getTypeArguments() {
    return getTypeArgumentList().getTypeArguments();
  }

  public PsiMethod resolveConstructor(){
    return (PsiMethod)resolveMethodGenerics().getElement();
  }

  public PsiJavaCodeReferenceElement getClassReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.TYPE_REFERENCE);
  }

  public PsiAnonymousClass getAnonymousClass() {
    ASTNode anonymousClass = findChildByType(JavaElementType.ANONYMOUS_CLASS);
    if (anonymousClass == null) return null;
    return (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass);
  }

  private static final TokenSet CLASS_REF = TokenSet.create(JavaElementType.JAVA_CODE_REFERENCE, JavaElementType.ANONYMOUS_CLASS);
  @Nullable
  public PsiJavaCodeReferenceElement getClassOrAnonymousClassReference() {
    ASTNode ref = findChildByType(CLASS_REF);
    if (ref == null) return null;
    if (ref instanceof PsiJavaCodeReferenceElement) return (PsiJavaCodeReferenceElement)ref;
    PsiAnonymousClass anonymousClass = (PsiAnonymousClass)ref.getPsi();
    return anonymousClass.getBaseClassReference();
  }

  public void deleteChildInternal(@NotNull ASTNode child) {
    if (getChildRole(child) == ChildRole.QUALIFIER){
      ASTNode dot = findChildByRole(ChildRole.DOT);
      super.deleteChildInternal(child);
      deleteChildInternal(dot);
    }
    else{
      super.deleteChildInternal(child);
    }
  }

  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.REFERENCE_PARAMETER_LIST:
        return findChildByType(JavaElementType.REFERENCE_PARAMETER_LIST);

      case ChildRole.QUALIFIER:
        TreeElement firstChild = getFirstChildNode();
        if (firstChild != null && firstChild.getElementType() != JavaTokenType.NEW_KEYWORD) {
          return firstChild;
        }
        else {
          return null;
        }

      case ChildRole.DOT:
        return findChildByType(JavaTokenType.DOT);

      case ChildRole.NEW_KEYWORD:
        return findChildByType(JavaTokenType.NEW_KEYWORD);

      case ChildRole.ANONYMOUS_CLASS:
        return findChildByType(JavaElementType.ANONYMOUS_CLASS);

      case ChildRole.TYPE_REFERENCE:
        return findChildByType(JavaElementType.JAVA_CODE_REFERENCE);

      case ChildRole.TYPE_KEYWORD:
        return findChildByType(ElementType.PRIMITIVE_TYPE_BIT_SET);

      case ChildRole.ARGUMENT_LIST:
        return findChildByType(JavaElementType.EXPRESSION_LIST);

      case ChildRole.LBRACKET:
        return findChildByType(JavaTokenType.LBRACKET);

      case ChildRole.RBRACKET:
        return findChildByType(JavaTokenType.RBRACKET);

      case ChildRole.ARRAY_INITIALIZER:
        if (getLastChildNode().getElementType() == JavaElementType.ARRAY_INITIALIZER_EXPRESSION){
          return getLastChildNode();
        }
        else{
          return null;
        }
    }
  }

  public int getChildRole(ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaElementType.REFERENCE_PARAMETER_LIST) {
      return ChildRole.REFERENCE_PARAMETER_LIST;
    }
    else if (i == JavaTokenType.NEW_KEYWORD) {
      return ChildRole.NEW_KEYWORD;
    }
    else if (i == JavaTokenType.DOT) {
      return ChildRole.DOT;
    }
    else if (i == JavaElementType.JAVA_CODE_REFERENCE) {
      return ChildRole.TYPE_REFERENCE;
    }
    else if (i == JavaElementType.EXPRESSION_LIST) {
      return ChildRole.ARGUMENT_LIST;
    }
    else if (i == JavaTokenType.LBRACKET) {
      return ChildRole.LBRACKET;
    }
    else if (i == JavaTokenType.RBRACKET) {
      return ChildRole.RBRACKET;
    }
    else if (i == JavaElementType.ARRAY_INITIALIZER_EXPRESSION) {
      if (child == getLastChildNode()) {
        return ChildRole.ARRAY_INITIALIZER;
      }
      else if (child == getFirstChildNode()) {
        return ChildRole.QUALIFIER;
      }
      else {
        return ChildRole.ARRAY_DIMENSION;
      }
    }
    else if (i == JavaElementType.ANONYMOUS_CLASS) {
      return ChildRole.ANONYMOUS_CLASS;
    }
    else {
      if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(child.getElementType())) {
        return ChildRole.TYPE_KEYWORD;
      }
      else if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return child == getFirstChildNode() ? ChildRole.QUALIFIER : ChildRole.ARRAY_DIMENSION;
      }
      else {
        return ChildRoleBase.NONE;
      }
    }
  }

  public void accept(@NotNull PsiElementVisitor visitor){
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNewExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString(){
    return "PsiNewExpression:" + getText();
  }
}

