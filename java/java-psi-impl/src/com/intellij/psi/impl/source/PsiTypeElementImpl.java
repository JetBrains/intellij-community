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

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  PsiTypeElementImpl(@NotNull IElementType type) {
    super(type);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiType getType() {
    return getTypeInfo().myType;
  }
  
  private static class TypeInfo {
    private final PsiType myType;
    private final boolean myInferred;

    private TypeInfo(PsiType type, boolean inferred) {
      myType = type;
      myInferred = inferred;
    }
  }
  
  private TypeInfo getTypeInfo() {
    return CachedValuesManager.getProjectPsiDependentCache(this, __ -> calculateTypeInfo());
  }

  private @NotNull TypeInfo calculateTypeInfo() {
    PsiType inferredType = PsiAugmentProvider.getInferredType(this);
    if (inferredType != null) {
      return new TypeInfo(inferredType, true);
    }

    PsiType type = null;
    boolean inferred = false;
    List<PsiAnnotation> annotations = new SmartList<>();

    PsiElement parent = getParent();
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) continue;

      if (child instanceof PsiAnnotation) {
        annotations.add((PsiAnnotation)child);
      }
      else if (child instanceof PsiTypeElement) {
        assert type == null : this;
        if (child instanceof PsiDiamondTypeElementImpl) {
          type = new PsiDiamondTypeImpl(getManager(), this);
          break;
        }
        else {
          type = ((PsiTypeElement)child).getType();
        }
      }
      else if (PsiUtil.isJavaToken(child, ElementType.PRIMITIVE_TYPE_BIT_SET)) {
        assert type == null : this;
        String text = child.getText();
        type = annotations.isEmpty() ? PsiJavaParserFacadeImpl.getPrimitiveType(text) : new PsiPrimitiveType(text, createProvider(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.VAR_KEYWORD)) {
        assert type == null : this;
        type = inferVarType(parent);
        inferred = true;
      }
      else if (child instanceof PsiJavaCodeReferenceElement) {
        assert type == null : this;
        type = new PsiClassReferenceType(getReferenceComputable((PsiJavaCodeReferenceElement)child), null, createProvider(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        assert type != null : this;
        type = new PsiArrayType(type, createProvider(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        assert type != null : this;
        type = new PsiEllipsisType(type, createProvider(annotations));
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST) ||
          child instanceof ASTNode && ((ASTNode)child).getElementType() == JavaElementType.DUMMY_ELEMENT && "any".equals(child.getText())) {
        assert type == null : this;
        PsiElement boundKind = PsiTreeUtil.skipWhitespacesAndCommentsForward(child);
        PsiElement boundType = PsiTreeUtil.skipWhitespacesAndCommentsForward(boundKind);
        if (PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createExtends(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else if (PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createSuper(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else {
          type = PsiWildcardType.createUnbounded(getManager());
        }
        type = type.annotate(createProvider(annotations));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.AND)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiIntersectionType.createIntersection(false, types.toArray(PsiType.createArray(types.size())));
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.OR)) {
        List<PsiType> types = collectTypes();
        assert !types.isEmpty() : this;
        type = PsiDisjunctionType.createDisjunction(types, getManager());
        break;
      }
    }

    if (type == null) return new TypeInfo(PsiType.NULL, inferred);

    if (parent instanceof PsiModifierListOwner) {
      type = JavaSharedImplUtil.applyAnnotations(type, ((PsiModifierListOwner)parent).getModifierList());
    }

    return new TypeInfo(type, inferred);
  }

  private PsiType inferVarType(PsiElement parent) {
    if (parent instanceof PsiParameter) {
      PsiElement declarationScope = ((PsiParameter)parent).getDeclarationScope();
      if (declarationScope instanceof PsiForeachStatement) {
        PsiExpression iteratedValue = ((PsiForeachStatement)declarationScope).getIteratedValue();
        if (iteratedValue != null) {
          return JavaGenericsUtil.getCollectionItemType(iteratedValue);
        }
        return null;
      }

      if (declarationScope instanceof PsiLambdaExpression) {
        return ((PsiParameter)parent).getType();
      }
    }
    else {
      for (PsiElement e = this; e != null; e = e.getNextSibling()) {
        if (e instanceof PsiExpression) {
          if (!(e instanceof PsiArrayInitializerExpression)) {
            PsiExpression expression = (PsiExpression)e;
            PsiType type = RecursionManager.doPreventingRecursion(expression, true, () -> expression.getType());
            return type == null ? null : JavaVarTypeUtil.getUpwardProjection(type);
          }
          return null;
        }
      }
    }
    return null;
  }

  @Override
  public boolean isInferredType() {
    return PsiUtil.isJavaToken(getFirstChild(), JavaTokenType.VAR_KEYWORD) || getTypeInfo().myInferred;
  }

  private @NotNull ClassReferencePointer getReferenceComputable(@NotNull PsiJavaCodeReferenceElement ref) {
    final PsiElement parent = getParent();
    if (parent instanceof PsiMethod || parent instanceof PsiVariable) {
      return computeFromTypeOwner(parent, new WeakReference<>(ref));
    }

    return ClassReferencePointer.constant(ref);
  }

  private static @NotNull ClassReferencePointer computeFromTypeOwner(PsiElement parent, @NotNull WeakReference<PsiJavaCodeReferenceElement> ref) {
    return new ClassReferencePointer() {
      volatile WeakReference<PsiJavaCodeReferenceElement> myCache = ref;

      @Override
      public @Nullable PsiJavaCodeReferenceElement retrieveReference() {
        PsiJavaCodeReferenceElement result = myCache.get();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (type instanceof PsiClassReferenceType) {
            result = ((PsiClassReferenceType)type).getReference();
          }
          myCache = new WeakReference<>(result);
        }
        return result;
      }

      @Nullable
      private PsiType calcTypeByParent() {
        PsiType type = parent instanceof PsiMethod ? ((PsiMethod)parent).getReturnType() : ((PsiVariable)parent).getType();
        if (type instanceof PsiArrayType) { //for c-style array, e.g. String args[]
          return type.getDeepComponentType();
        }
        return type;
      }

      @Override
      public @NotNull PsiJavaCodeReferenceElement retrieveNonNullReference() {
        PsiJavaCodeReferenceElement result = retrieveReference();
        if (result == null) {
          PsiType type = calcTypeByParent();
          if (!(type instanceof PsiClassReferenceType)) {
            PsiUtilCore.ensureValid(parent);
            throw new IllegalStateException("No reference type for " + parent.getClass() + "; type: " + (type != null ? type.getClass() : "null"));
          }
          result = ((PsiClassReferenceType)type).getReference();
        }
        return result;
      }

      @Override
      public String toString() {
        String msg = "Type element reference of " + parent.getClass() + " #" + parent.getClass().getSimpleName();
        return parent.isValid() ? msg + " #" + parent.getLanguage() : msg + ", invalid";
      }
    };
  }

  private static @NotNull TypeAnnotationProvider createProvider(@NotNull List<PsiAnnotation> annotations) {
    return TypeAnnotationProvider.Static.create(ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true));
  }

  private @NotNull List<PsiType> collectTypes() {
    List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
    return ContainerUtil.map(typeElements, typeElement -> typeElement.getType());
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
    }
    return getReferenceElement();
  }

  private @Nullable PsiJavaCodeReferenceElement getReferenceElement() {
    ASTNode ref = findChildByType(JavaElementType.JAVA_CODE_REFERENCE);
    if (ref == null) return null;
    return (PsiJavaCodeReferenceElement)SourceTreeToPsiMap.treeElementToPsi(ref);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    return true;
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(this, PsiAnnotation.class);
    return annotations != null ? annotations : PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getType().getAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    PsiAnnotation annotation = JavaPsiFacade.getElementFactory(getProject()).createAnnotationFromText('@' + qualifiedName, this);
    PsiElement firstChild = getFirstChild();
    for (PsiElement child = getLastChild(); child != firstChild; child = child.getPrevSibling()) {
      if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET) || PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        return (PsiAnnotation)addBefore(annotation, child);
      }
    }
    if (firstChild instanceof PsiJavaCodeReferenceElement) {
      PsiIdentifier identifier = PsiTreeUtil.getChildOfType(firstChild, PsiIdentifier.class);
      if (identifier != null && identifier != firstChild.getFirstChild()) {
        // qualified reference
        return (PsiAnnotation)firstChild.addBefore(annotation, identifier);
      }
    }
    PsiElement parent = getParent();
    while (parent instanceof PsiTypeElement && ((PsiTypeElement)parent).getType() instanceof PsiArrayType) {
      parent = parent.getParent();
    }
    if (parent instanceof PsiModifierListOwner) {
      PsiModifierList modifierList = ((PsiModifierListOwner)parent).getModifierList();
      if (modifierList != null) {
        PsiTypeParameterList list = parent instanceof PsiTypeParameterListOwner ? ((PsiTypeParameterListOwner)parent).getTypeParameterList() : null;
        if (list == null || list.textMatches("")) {
          return (PsiAnnotation)modifierList.add(annotation);
        }
      }
    }
    return (PsiAnnotation)addBefore(annotation, firstChild);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // neighbouring type annotations are logical part of this type element and should be dropped
    //if replacement is `var`, annotations should be left as they are not inferred from the right side of the assignment
    if (!(newElement instanceof PsiTypeElement) || !((PsiTypeElement)newElement).isInferredType()) {
      PsiImplUtil.markTypeAnnotations(this);
    }
    PsiElement result = super.replace(newElement);
    if (result instanceof PsiTypeElement) {
      PsiImplUtil.deleteTypeAnnotations((PsiTypeElement)result);
    }
    return result;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}