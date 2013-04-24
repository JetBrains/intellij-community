/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PsiTypeElementImpl extends CompositePsiElement implements PsiTypeElement {
  private volatile PsiType myCachedType = null;

  @SuppressWarnings({"UnusedDeclaration"})
  public PsiTypeElementImpl() {
    this(JavaElementType.TYPE);
  }

  protected PsiTypeElementImpl(IElementType type) {
    super(type);
  }

  @Override
  public void clearCaches() {
    super.clearCaches();
    myCachedType = null;
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
    PsiType cachedType = myCachedType;
    if (cachedType != null) return cachedType;
    cachedType = calculateType();
    myCachedType = cachedType;
    return cachedType;
  }

  private PsiType calculateType() {
    PsiType type = null;
    SmartList<PsiAnnotation> annotations = new SmartList<PsiAnnotation>();

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
        addTypeUseAnnotations(annotations);
        PsiAnnotation[] array = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = JavaPsiFacade.getInstance(getProject()).getElementFactory().createPrimitiveType(child.getText(), array);
      }
      else if (child instanceof PsiJavaCodeReferenceElement) {
        assert type == null : this;
        addTypeUseAnnotations(annotations);
        PsiAnnotation[] array = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = new PsiClassReferenceType((PsiJavaCodeReferenceElement)child, null, array);
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.LBRACKET)) {
        assert type != null : this;
        PsiAnnotation[] array = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = type.createArrayType(array);
      }
      else if (PsiUtil.isJavaToken(child, JavaTokenType.ELLIPSIS)) {
        assert type != null : this;
        PsiAnnotation[] array = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = PsiEllipsisType.createEllipsis(type, array);
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.QUEST)) {
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
        PsiAnnotation[] array = ContainerUtil.copyAndClear(annotations, PsiAnnotation.ARRAY_FACTORY, true);
        type = ((PsiWildcardType)type).annotate(array);
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.AND)) {
        List<PsiType> types = collectTypes();
        assert types.size() > 0 : this;
        type = PsiIntersectionType.createIntersection(types);
        break;
      }

      if (PsiUtil.isJavaToken(child, JavaTokenType.OR)) {
        List<PsiType> types = collectTypes();
        assert types.size() > 0 : this;
        type = PsiDisjunctionType.createDisjunction(types, getManager());
        break;
      }
    }

    return type == null ? PsiType.NULL : type;
  }

  private void addTypeUseAnnotations(List<PsiAnnotation> list) {
    PsiElement parent = this;
    while (parent instanceof PsiTypeElement) {
      PsiElement left = PsiTreeUtil.skipSiblingsBackward(parent, PsiComment.class, PsiWhiteSpace.class, PsiAnnotation.class);

      if (left instanceof PsiModifierList) {
        List<PsiAnnotation> annotations = PsiImplUtil.getTypeUseAnnotations((PsiModifierList)left);
        if (annotations != null && annotations.size() > 0) {
          list.addAll(annotations);
        }
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
  public PsiType getTypeNoResolve(@NotNull PsiElement context) {
    return getType();
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

  @Override
  public PsiAnnotationOwner getOwner(@NotNull PsiAnnotation annotation) {
    return this;
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
    addTypeUseAnnotations(annotations);
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
    PsiElement result = super.replace(newElement);

    // We want to reformat method call arguments on method return type change because there is a possible situation that they are aligned
    // and the change breaks the alignment.
    // Example:
    //     Object test(1,
    //                 2) {}
    // Suppose we're changing return type to 'MyCustomClass'. We get the following if parameter list is not reformatted:
    //     MyCustomClass test(1,
    //                 2) {}
    PsiElement parent = result.getParent();
    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      CodeEditUtil.markToReformat(method.getParameterList().getNode(), true);
    }

    // We cover situation like below here:
    //     int test(int i, int j) {}
    //     ...
    //     int i = test(1,
    //                  2);
    // I.e. the point is to avoid code like below during changing 'test()' return type from 'int' to 'long':
    //     long i = test(1,
    //                  2);
    else if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;
      if (variable.hasInitializer()) {
        PsiExpression methodCallCandidate = variable.getInitializer();
        if (methodCallCandidate instanceof PsiMethodCallExpression) {
          PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)methodCallCandidate;
          CodeEditUtil.markToReformat(methodCallExpression.getArgumentList().getNode(), true);
        }
      }
    }

    return result;
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
