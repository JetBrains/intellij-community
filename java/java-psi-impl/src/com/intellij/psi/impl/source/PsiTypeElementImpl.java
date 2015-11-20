/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private static final Class[] INTERMEDIATES = {PsiComment.class, PsiWhiteSpace.class, PsiAnnotation.class, PsiTypeParameterList.class};

  @SuppressWarnings({"UnusedDeclaration"})
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  protected PsiTypeElementImpl(IElementType type) {
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
  @NotNull
  public PsiType getType() {
    return CachedValuesManager.getCachedValue(this, new CachedValueProvider<PsiType>() {
      @Nullable
      @Override
      public Result<PsiType> compute() {
        return Result.create(calculateType(), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  private PsiType calculateType() {
    PsiType inferredType = PsiAugmentProvider.getInferredType(this);
    if (inferredType != null) {
      return inferredType;
    }

    PsiType type = null;
    final SmartList<PsiAnnotation> annotations = new SmartList<PsiAnnotation>();

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
        addTypeUseCandidateAnnotations(annotations);
        String text = child.getText();
        type = annotations.isEmpty() ? PsiJavaParserFacadeImpl.getPrimitiveType(text)
                                     : new PsiPrimitiveType(text, filterTypeUse(annotations));
      }
      else if (child instanceof PsiJavaCodeReferenceElement) {
        assert type == null : this;
        addTypeUseCandidateAnnotations(annotations);
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)child, null, filterTypeUse(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        assert type != null : this;
        type = new PsiArrayType(type, filterTypeUse(annotations));
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        assert type != null : this;
        type = new PsiEllipsisType(type, filterTypeUse(annotations));
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST) ||
          child instanceof ASTNode && ((ASTNode)child).getElementType() == JavaElementType.DUMMY_ELEMENT && "any".equals(child.getText())) {
        assert type == null : this;
        PsiElement boundKind = PsiTreeUtil.skipSiblingsForward(child, PsiComment.class, PsiWhiteSpace.class);
        PsiElement boundType = PsiTreeUtil.skipSiblingsForward(boundKind, PsiComment.class, PsiWhiteSpace.class);
        if (PsiUtil.isJavaToken(boundKind, JavaTokenType.EXTENDS_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createExtends(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else if (PsiUtil.isJavaToken(boundKind, JavaTokenType.SUPER_KEYWORD) && boundType instanceof PsiTypeElement) {
          type = PsiWildcardType.createSuper(getManager(), ((PsiTypeElement)boundType).getType());
        }
        else {
          type = PsiWildcardType.createUnbounded(getManager());
        }
        type = ((PsiWildcardType)type).annotate(filterTypeUse(annotations));
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

    return type == null ? PsiType.NULL : type;
  }

  @NotNull
  private static TypeAnnotationProvider filterTypeUse(List<PsiAnnotation> annotations) {
    if (annotations.isEmpty()) return TypeAnnotationProvider.EMPTY;

    final ArrayList<PsiAnnotation> copy = ContainerUtil.newArrayList(annotations);
    annotations.clear();
    return new TypeAnnotationProvider() {
      private volatile PsiAnnotation[] myCached;
      @NotNull
      @Override
      public PsiAnnotation[] getAnnotations() {
        PsiAnnotation[] result = myCached;
        if (result == null) {
          List<PsiAnnotation> filtered = ContainerUtil.filter(copy, new Condition<PsiAnnotation>() {
            @Override
            public boolean value(PsiAnnotation annotation) {
              return PsiImplUtil.isTypeAnnotation(annotation);
            }
          });
          myCached = result = filtered.isEmpty() ? PsiAnnotation.EMPTY_ARRAY : filtered.toArray(new PsiAnnotation[filtered.size()]);
        }
        return result;
      }
    };
  }

  private void addTypeUseCandidateAnnotations(List<PsiAnnotation> annotations) {
    PsiElement parent = this;
    while (parent instanceof PsiTypeElement) {
      PsiElement left = PsiTreeUtil.skipSiblingsBackward(parent, INTERMEDIATES);

      if (left instanceof PsiModifierList) {
        Collections.addAll(annotations, ((PsiModifierList)left).getAnnotations());
        break;
      }

      if (left != null) break;

      parent = parent.getParent();
    }
  }

  private List<PsiType> collectTypes() {
    List<PsiTypeElement> typeElements = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiTypeElement.class);
    return ContainerUtil.map(typeElements, new Function<PsiTypeElement, PsiType>() {
      @Override
      public PsiType fun(PsiTypeElement typeElement) {
        return typeElement.getType();
      }
    });
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    TreeElement firstChildNode = getFirstChildNode();
    if (firstChildNode == null) return null;
    if (firstChildNode.getElementType() == JavaElementType.TYPE) {
      return SourceTreeToPsiMap.<PsiTypeElement>treeToPsiNotNull(firstChildNode).getInnermostComponentReferenceElement();
    }
    else {
      return getReferenceElement();
    }
  }

  @Nullable
  private PsiJavaCodeReferenceElement getReferenceElement() {
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
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    PsiAnnotation[] annotations = PsiTreeUtil.getChildrenOfType(this, PsiAnnotation.class);
    return annotations != null ? annotations : PsiAnnotation.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public PsiAnnotation[] getApplicableAnnotations() {
    List<PsiAnnotation> annotations = PsiTreeUtil.getChildrenOfTypeAsList(this, PsiAnnotation.class);
    addTypeUseCandidateAnnotations(annotations);
    return annotations.toArray(PsiAnnotation.ARRAY_FACTORY.create(annotations.size()));
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // neighbouring type annotations are logical part of this type element and should be dropped
    PsiImplUtil.markTypeAnnotations(this);
    PsiElement result = super.replace(newElement);
    PsiImplUtil.deleteTypeAnnotations((PsiTypeElement)result);
    return result;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
