// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.java.stubs.*;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaClassStubFactory implements LightStubElementFactory<PsiClassStubImpl<PsiClass>, PsiClass> {
  @Override
  public @NotNull PsiClassStubImpl<PsiClass> createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    boolean isDeprecatedByComment = false;
    boolean isInterface = false;
    boolean isEnum = false;
    boolean isRecord = false;
    boolean isValueClass = false;
    boolean isEnumConst = false;
    boolean isAnonymous = false;
    boolean isAnnotation = false;
    boolean isInQualifiedNew = false;
    boolean classKindFound = false;
    boolean hasDeprecatedAnnotation = false;
    boolean hasDocComment = false;

    String qualifiedName = null;
    String name = null;
    String baseRef = null;

    if (node.getTokenType() == JavaElementType.ANONYMOUS_CLASS) {
      isAnonymous = true;
      classKindFound = true;
    }
    else if (node.getTokenType() == JavaElementType.ENUM_CONSTANT_INITIALIZER) {
      isAnonymous = isEnumConst = true;
      classKindFound = true;
      baseRef = ((PsiClassStub<?>)parentStub.getParentStub()).getName();
    }

    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        hasDocComment = true;
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
        isValueClass = RecordUtil.hasValueModifier(tree, child);
      }
      else if (type == JavaTokenType.AT) {
        isAnnotation = true;
      }
      else if (type == JavaTokenType.INTERFACE_KEYWORD && !classKindFound) {
        isInterface = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.ENUM_KEYWORD && !classKindFound) {
        isEnum = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.RECORD_KEYWORD && !classKindFound) {
        isRecord = true;
        classKindFound = true;
      }
      else if (type == JavaTokenType.CLASS_KEYWORD) {
        classKindFound = true;
      }
      else if (!isAnonymous && type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (isAnonymous && !isEnumConst && type == JavaElementType.JAVA_CODE_REFERENCE) {
        baseRef = LightTreeUtil.toFilteredString(tree, child, null);
      }
    }

    if (name != null) {
      if (parentStub instanceof PsiJavaFileStub) {
        final String pkg = ((PsiJavaFileStub)parentStub).getPackageName();
        if (!pkg.isEmpty()) qualifiedName = pkg + '.' + name; else qualifiedName = name;
      }
      else if (parentStub instanceof PsiClassStub) {
        final String parentFqn = ((PsiClassStub<?>)parentStub).getQualifiedName();
        qualifiedName = parentFqn != null ? parentFqn + '.' + name : null;
      }
    }

    if (isAnonymous) {
      final LighterASTNode parent = tree.getParent(node);
      if (parent != null && parent.getTokenType() == JavaElementType.NEW_EXPRESSION) {
        isInQualifiedNew = LightTreeUtil.firstChildOfType(tree, parent, JavaTokenType.DOT) != null;
      }
    }


    boolean isImplicit = node.getTokenType() == JavaElementType.IMPLICIT_CLASS;
    final short flags = PsiClassStubImpl.packFlags(isDeprecatedByComment, isInterface, isEnum, isEnumConst, isAnonymous, isAnnotation,
                                                   isInQualifiedNew, hasDeprecatedAnnotation, false, false, hasDocComment, isRecord,
                                                   isImplicit, isValueClass);
    final JavaClassElementType type = typeForClass(isAnonymous, isEnumConst, isImplicit);
    return new PsiClassStubImpl<>(type, parentStub, qualifiedName, name, baseRef, flags);
  }

  @Override
  public PsiClass createPsi(@NotNull PsiClassStubImpl<PsiClass> stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createClass(stub);
  }
  
  @Override
  public @NotNull PsiClassStubImpl<PsiClass> createStub(@NotNull PsiClass psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }

  public static @NotNull JavaClassElementType typeForClass(final boolean anonymous, final boolean enumConst, final boolean implicitClass) {
    return enumConst
           ? JavaStubElementTypes.ENUM_CONSTANT_INITIALIZER
           : implicitClass ? JavaStubElementTypes.IMPLICIT_CLASS
                           : anonymous ? JavaStubElementTypes.ANONYMOUS_CLASS : JavaStubElementTypes.CLASS;
  }
}