// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.PsiJavaParserFacadeImpl;
import com.intellij.psi.impl.cache.TypeAnnotationContainer;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.util.NotNullLazyValue.atomicLazy;
import static com.intellij.openapi.util.NullableLazyValue.atomicLazyNullable;

public class ClsTypeElementImpl extends ClsElementImpl implements PsiTypeElement {
  static final char VARIANCE_NONE = '\0';
  static final char VARIANCE_EXTENDS = '+';
  static final char VARIANCE_SUPER = '-';
  static final char VARIANCE_INVARIANT = '*';

  private final PsiElement myParent;
  private final @NotNull String myTypeText;
  private final char myVariance;
  private final @NotNull TypeAnnotationContainer myAnnotations;
  private final @NotNull NullableLazyValue<ClsElementImpl> myChild;
  private final @NotNull NotNullLazyValue<PsiType> myCachedType;

  public ClsTypeElementImpl(@NotNull PsiElement parent,
                            @NotNull String typeText,
                            char variance) {
    this(parent, typeText, variance, TypeAnnotationContainer.EMPTY);
  }

  ClsTypeElementImpl(@Nullable PsiElement parent, @NotNull TypeInfo typeInfo) {
    this(parent, Objects.requireNonNull(TypeInfo.createTypeText(typeInfo)), VARIANCE_NONE, typeInfo.getTypeAnnotations());
  }

  ClsTypeElementImpl(@Nullable PsiElement parent,
                     @NotNull String typeText,
                     char variance,
                     @NotNull TypeAnnotationContainer annotations) {
    myParent = parent;
    myTypeText = TypeInfo.internFrequentType(typeText);
    myVariance = variance;
    myAnnotations = annotations;
    myChild = atomicLazyNullable(() -> calculateChild());
    myCachedType = atomicLazy(() -> calculateType());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    ClsElementImpl child = myChild.getValue();
    return child != null ? new PsiElement[]{child} : PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public String getText() {
    final String shortClassName = PsiNameHelper.getShortClassName(myTypeText);
    return decorateTypeText(shortClassName);
  }

  private String decorateTypeText(final String shortClassName) {
    switch (myVariance) {
      case VARIANCE_NONE:
        return shortClassName;
      case VARIANCE_EXTENDS:
        return PsiWildcardType.EXTENDS_PREFIX + shortClassName;
      case VARIANCE_SUPER:
        return PsiWildcardType.SUPER_PREFIX + shortClassName;
      case VARIANCE_INVARIANT:
        return "?";
      default:
        assert false : myVariance;
        return null;
    }
  }

  public String getCanonicalText() {
    return decorateTypeText(myTypeText);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append(getType().getCanonicalText(true));
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, JavaElementType.TYPE);
    PsiTypeElement mirror = SourceTreeToPsiMap.treeToPsiNotNull(element);
    setMirrorIfPresent(getInnermostComponentReferenceElement(), mirror.getInnermostComponentReferenceElement());
  }

  private boolean isArray() {
    return myTypeText.endsWith("[]");
  }

  private boolean isVarArgs() {
    return myTypeText.endsWith("...");
  }

  @Override
  @NotNull
  public PsiType getType() {
    return myCachedType.getValue();
  }

  @Override
  public PsiJavaCodeReferenceElement getInnermostComponentReferenceElement() {
    ClsElementImpl child = myChild.getValue();
    if (child instanceof ClsTypeElementImpl) {
      return ((ClsTypeElementImpl)child).getInnermostComponentReferenceElement();
    } else {
      return (PsiJavaCodeReferenceElement) child;
    }
  }

  private ClsElementImpl calculateChild() {
    if (PsiJavaParserFacadeImpl.getPrimitiveType(myTypeText) != null) {
      return null;
    }
    if (isArray()) {
      if (myVariance == VARIANCE_NONE) {
        return getDeepestArrayElement();
      }
      return new ClsTypeElementImpl(this, myTypeText, VARIANCE_NONE, myAnnotations.forBound());
    }
    if (isVarArgs()) {
      return getDeepestArrayElement();
    }
    return myVariance == VARIANCE_INVARIANT ? null :
           new ClsJavaCodeReferenceElementImpl(this, myTypeText, myVariance == VARIANCE_NONE ? myAnnotations : myAnnotations.forBound());
  }

  int getArrayDepth() {
    boolean varArgs = isVarArgs();
    if (!varArgs && !isArray()) return 0;
    int bracketPos = myTypeText.length() - (varArgs ? 3 : 2);
    int depth = 1;
    while (bracketPos > 2 && myTypeText.startsWith("[]", bracketPos - 2)) {
      bracketPos -= 2;
      depth++;
    }
    return depth;
  }

  @NotNull
  private ClsElementImpl getDeepestArrayElement() {
    int depth = getArrayDepth();
    int bracketPos = myTypeText.length() - depth * 2 - (isVarArgs() ? 1 : 0);
    TypeAnnotationContainer container = myAnnotations;
    for (int i = 0; i < depth; i++) {
      container = container.forArrayElement();
    }
    return new ClsTypeElementImpl(this, myTypeText.substring(0, bracketPos), myVariance, container);
  }

  @NotNull
  private PsiType createArrayType(PsiTypeElement deepestChild) {
    int depth = getArrayDepth();
    List<TypeAnnotationContainer> containers =
      StreamEx.iterate(myAnnotations, TypeAnnotationContainer::forArrayElement).limit(depth).toList();
    PsiType type = deepestChild.getType();
    for (int i = depth - 1; i >= 0; i--) {
      if (i == 0 && isVarArgs()) {
        type = new PsiEllipsisType(type);
      } else {
        type = type.createArrayType();
      }
      type = type.annotate(containers.get(i).getProvider(this));
    }
    return type;
  }

  @NotNull
  private PsiType calculateType() {
    return calculateBaseType().annotate(myAnnotations.getProvider(this));
  }

  @NotNull
  private PsiType calculateBaseType() {
    PsiType result = PsiJavaParserFacadeImpl.getPrimitiveType(myTypeText);
    if (result != null) return result;

    ClsElementImpl childElement = myChild.getValue();
    if (childElement instanceof ClsTypeElementImpl) {
      if (isArray()) {
        switch (myVariance) {
          case VARIANCE_NONE:
            return createArrayType((PsiTypeElement)childElement);
          case VARIANCE_EXTENDS:
            return PsiWildcardType.createExtends(getManager(), ((PsiTypeElement)childElement).getType());
          case VARIANCE_SUPER:
            return PsiWildcardType.createSuper(getManager(), ((PsiTypeElement)childElement).getType());
          default:
            assert false : myVariance;
            return null;
        }
      }
      else {
        assert isVarArgs() : this;
        return createArrayType((PsiTypeElement)childElement);
      }
    }
    if (childElement instanceof ClsJavaCodeReferenceElementImpl) {
      PsiClassReferenceType psiClassReferenceType = new PsiClassReferenceType((PsiJavaCodeReferenceElement)childElement, null);
      switch (myVariance) {
        case VARIANCE_NONE:
          return psiClassReferenceType;
        case VARIANCE_EXTENDS:
          return PsiWildcardType.createExtends(getManager(), psiClassReferenceType.annotate(myAnnotations.forBound().getProvider(childElement)));
        case VARIANCE_SUPER:
          return PsiWildcardType.createSuper(getManager(), psiClassReferenceType.annotate(myAnnotations.forBound().getProvider(childElement)));
        case VARIANCE_INVARIANT:
          return PsiWildcardType.createUnbounded(getManager());
        default:
          assert false : myVariance;
          return null;
      }
    }
    assert childElement == null : this;
    return PsiWildcardType.createUnbounded(getManager());
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
  public PsiAnnotation @NotNull [] getAnnotations() {
    throw new UnsupportedOperationException();//todo
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return PsiImplUtil.findAnnotation(this, qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull String qualifiedName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getType().getAnnotations();
  }

  @Override
  public String toString() {
    return "PsiTypeElement:" + getText();
  }
}
