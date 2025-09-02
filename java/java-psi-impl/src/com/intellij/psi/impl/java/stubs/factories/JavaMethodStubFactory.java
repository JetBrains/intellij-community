// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaMethodStubFactory implements LightStubElementFactory<PsiMethodStubImpl, PsiMethod> {
  @Override
  public @NotNull PsiMethodStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    String name = null;
    boolean isConstructor = true;
    boolean isVarArgs = false;
    boolean isDeprecatedByComment = false;
    boolean hasDeprecatedAnnotation = false;
    boolean hasDocComment = false;
    String defValueText = null;

    boolean expectingDef = false;
    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        hasDocComment = true;
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
      }
      else if (type == JavaElementType.TYPE) {
        isConstructor = false;
      }
      else if (type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (type == JavaElementType.PARAMETER_LIST) {
        final List<LighterASTNode> params = LightTreeUtil.getChildrenOfType(tree, child, JavaElementType.PARAMETER);
        if (!params.isEmpty()) {
          final LighterASTNode pType = LightTreeUtil.firstChildOfType(tree, params.get(params.size() - 1), JavaElementType.TYPE);
          if (pType != null) {
            isVarArgs = LightTreeUtil.firstChildOfType(tree, pType, JavaTokenType.ELLIPSIS) != null;
          }
        }
      }
      else if (type == JavaTokenType.DEFAULT_KEYWORD) {
        expectingDef = true;
      }
      else if (expectingDef && !ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(type) &&
               type != JavaTokenType.SEMICOLON && type != JavaElementType.CODE_BLOCK) {
        defValueText = LightTreeUtil.toFilteredString(tree, child, null);
        break;
      }
    }

    TypeInfo typeInfo = isConstructor ? TypeInfo.createConstructorType() : TypeInfo.create(tree, node, parentStub);
    boolean isAnno = node.getTokenType() == JavaElementType.ANNOTATION_METHOD;
    byte flags = PsiMethodStubImpl.packFlags(isConstructor, isAnno, isVarArgs, isDeprecatedByComment, hasDeprecatedAnnotation, hasDocComment);

    return new PsiMethodStubImpl(parentStub, name, typeInfo, flags, defValueText);
  }

  @Override
  public PsiMethod createPsi(@NotNull PsiMethodStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createMethod(stub);
  }
  
  @Override
  public @NotNull PsiMethodStubImpl createStub(@NotNull PsiMethod psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
}