// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiPolyVariantCachingReference;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PsiNewExpressionImpl extends ExpressionPsiElement implements PsiNewExpression {
  private static final Logger LOG = Logger.getInstance(PsiNewExpressionImpl.class);

  public PsiNewExpressionImpl() {
    super(JavaElementType.NEW_EXPRESSION);
  }

  @Override
  public PsiType getType() {
    return doGetType(null);
  }

  @Override
  public PsiType getOwner(@NotNull PsiAnnotation annotation) {
    assert annotation.getParent() == this : annotation.getParent() + " != " + this;
    return doGetType(annotation);
  }

  private @Nullable PsiType doGetType(@Nullable PsiAnnotation stopAt) {
    PsiType type = null;
    List<PsiAnnotation> annotations = new SmartList<>();
    List<TypeAnnotationProvider> arrayComponentAnnotations = new ArrayList<>();
    boolean stop = false;

    for (ASTNode child = getFirstChildNode(); child != null; child = child.getTreeNext()) {
      IElementType elementType = child.getElementType();
      if (elementType == JavaElementType.ANNOTATION) {
        PsiAnnotation annotation = (PsiAnnotation)child.getPsi();
        annotations.add(annotation);
        if (annotation == stopAt) {
          // Drop previous array annotations. Only the subsequent annotations matter
          // E.g., if we have "new int @A [] @B [] @C []" and we search an owner for "@B"
          // then we should return "new int @B [] @C []"
          arrayComponentAnnotations.clear();
          stop = true;
        }
      }
      else if (elementType == JavaElementType.JAVA_CODE_REFERENCE) {
        assert type == null : this;
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)child.getPsi(), null);
        if (stop) return type;
      }
      else if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(elementType)) {
        assert type == null : this;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        PsiAnnotation[] copy = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = factory.createPrimitiveTypeFromText(child.getText()).annotate(TypeAnnotationProvider.Static.create(copy));
        if (stop) return type;
      }
      else if (elementType == JavaTokenType.LBRACKET) {
        assert type != null : this;
        PsiAnnotation[] copy = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        arrayComponentAnnotations.add(TypeAnnotationProvider.Static.create(copy));
      }
      else if (elementType == JavaElementType.ANONYMOUS_CLASS) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(getProject());
        PsiClass aClass = (PsiClass)child.getPsi();
        PsiSubstitutor substitutor = aClass instanceof PsiTypeParameter ? PsiSubstitutor.EMPTY : factory.createRawSubstitutor(aClass);
        PsiAnnotation[] copy = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = factory.createType(aClass, substitutor, PsiUtil.getLanguageLevel(aClass)).annotate(TypeAnnotationProvider.Static.create(copy));
        if (stop) return type;
      }
    }

    for (int i = arrayComponentAnnotations.size() - 1; i >= 0; i--) {
      TypeAnnotationProvider provider = arrayComponentAnnotations.get(i);
      type = new PsiArrayType(type, provider);
    }
    return type;
  }

  @Override
  public PsiExpressionList getArgumentList() {
    PsiExpressionList list = (PsiExpressionList)findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    if (list != null) return list;
    CompositeElement anonymousClass = (CompositeElement)SourceTreeToPsiMap.psiElementToTree(findChildByRoleAsPsiElement(ChildRole.ANONYMOUS_CLASS));
    if (anonymousClass != null){
      return (PsiExpressionList)anonymousClass.findChildByRoleAsPsiElement(ChildRole.ARGUMENT_LIST);
    }
    return null;
  }

  @Override
  public PsiExpression @NotNull [] getArrayDimensions() {
    PsiExpression[] expressions = getChildrenAsPsiElements(ElementType.ARRAY_DIMENSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
    PsiExpression qualifier = getQualifier();
    if (qualifier == null ||
        //invalid qualifier
        !ElementType.ARRAY_DIMENSION_BIT_SET.contains(qualifier.getNode().getElementType())) {
      return expressions;
    }
    else {
      LOG.assertTrue(expressions[0] == qualifier);
      return Arrays.copyOfRange(expressions, 1, expressions.length);
    }
  }

  @Override
  public PsiArrayInitializerExpression getArrayInitializer() {
    return (PsiArrayInitializerExpression)findChildByRoleAsPsiElement(ChildRole.ARRAY_INITIALIZER);
  }

  @Override
  public PsiMethod resolveMethod() {
    return resolveConstructor();
  }

  private PsiPolyVariantCachingReference getConstructorFakeReference() {
    return CachedValuesManager.getCachedValue(this, () -> new CachedValueProvider.Result<>(new PsiPolyVariantCachingReference() {
      @Override
      public JavaResolveResult @NotNull [] resolveInner(boolean incompleteCode, @NotNull PsiFile containingFile) {
        ASTNode classRef = findChildByRole(ChildRole.TYPE_REFERENCE);
        if (classRef != null) {
          ASTNode argumentList = PsiImplUtil.skipWhitespaceAndComments(classRef.getTreeNext());
          if (argumentList != null && argumentList.getElementType() == JavaElementType.EXPRESSION_LIST) {
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(containingFile.getProject());
            PsiClassType
              aClass = facade.getElementFactory().createType((PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(classRef));
            return facade.getResolveHelper().multiResolveConstructor(aClass,
                                                                     (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                     PsiNewExpressionImpl.this);
          }
        }
        else{
          ASTNode anonymousClassElement = findChildByType(JavaElementType.ANONYMOUS_CLASS);
          if (anonymousClassElement != null) {
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(containingFile.getProject());
            final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClassElement);
            PsiClassType aClass = anonymousClass.getBaseClassType();
            ASTNode argumentList = anonymousClassElement.findChildByType(JavaElementType.EXPRESSION_LIST);
            return facade.getResolveHelper().multiResolveConstructor(aClass,
                                                                     (PsiExpressionList)SourceTreeToPsiMap.treeElementToPsi(argumentList),
                                                                     anonymousClass);
          }
        }
        return JavaResolveResult.EMPTY_ARRAY;
      }

      @Override
      public @NotNull PsiElement getElement() {
        return PsiNewExpressionImpl.this;
      }

      @Override
      public @NotNull TextRange getRangeInElement() {
        return null;
      }

      @Override
      public @NotNull String getCanonicalText() {
        throw new UnsupportedOperationException();
      }

      @Override
      public PsiElement handleElementRename(@NotNull String newElementName) {
        return null;
      }

      @Override
      public PsiElement bindToElement(@NotNull PsiElement element) {
        return null;
      }

      @Override
      public int hashCode() {
        return getElement().hashCode();
      }

      @Override
      public boolean equals(Object obj) {
        return obj instanceof PsiPolyVariantCachingReference && getElement() == ((PsiReference)obj).getElement();
      }
    }, PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Override
  public @NotNull JavaResolveResult resolveMethodGenerics() {
    JavaResolveResult[] results = multiResolve(false);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  @Override
  public JavaResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    ResolveResult[] results = getConstructorFakeReference().multiResolve(incompleteCode);
    if (results instanceof JavaResolveResult[]) return (JavaResolveResult[])results;
    return ContainerUtil.map2Array(results, JavaResolveResult.EMPTY_ARRAY, result -> (JavaResolveResult)result);
  }

  @Override
  public PsiExpression getQualifier() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.QUALIFIER);
  }

  @Override
  public @NotNull PsiReferenceParameterList getTypeArgumentList() {
    return (PsiReferenceParameterList) findChildByRoleAsPsiElement(ChildRole.REFERENCE_PARAMETER_LIST);
  }

  @Override
  public PsiType @NotNull [] getTypeArguments() {
    return getTypeArgumentList().getTypeArguments();
  }

  @Override
  public PsiMethod resolveConstructor(){
    return (PsiMethod)resolveMethodGenerics().getElement();
  }

  @Override
  public PsiJavaCodeReferenceElement getClassReference() {
    return (PsiJavaCodeReferenceElement)findChildByRoleAsPsiElement(ChildRole.TYPE_REFERENCE);
  }

  @Override
  public PsiAnonymousClass getAnonymousClass() {
    ASTNode anonymousClass = findChildByType(JavaElementType.ANONYMOUS_CLASS);
    if (anonymousClass == null) return null;
    return (PsiAnonymousClass)SourceTreeToPsiMap.treeElementToPsi(anonymousClass);
  }

  private static final TokenSet CLASS_REF = TokenSet.create(JavaElementType.JAVA_CODE_REFERENCE, JavaElementType.ANONYMOUS_CLASS);
  @Override
  public @Nullable PsiJavaCodeReferenceElement getClassOrAnonymousClassReference() {
    ASTNode ref = findChildByType(CLASS_REF);
    if (ref == null) return null;
    if (ref instanceof PsiJavaCodeReferenceElement) return (PsiJavaCodeReferenceElement)ref;
    PsiAnonymousClass anonymousClass = (PsiAnonymousClass)ref.getPsi();
    return anonymousClass.getBaseClassReference();
  }

  @Override
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

  @Override
  public ASTNode findChildByRole(int role){
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
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

      default:
        return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
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

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNewExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiNewExpression:" + getText();
  }
}
