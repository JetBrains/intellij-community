// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiFieldStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.PsiEnumConstantImpl;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.EnumConstantElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class JavaFieldStubElementType extends JavaStubElementType<PsiFieldStub, PsiField> {
  private static final int INITIALIZER_LENGTH_LIMIT = 1000;

  public JavaFieldStubElementType(@NotNull String id) {
    super(id);
  }

  @Override
  public PsiField createPsi(@NotNull final PsiFieldStub stub) {
    return getPsiFactory(stub).createField(stub);
  }

  @Override
  public PsiField createPsi(@NotNull final ASTNode node) {
    if (node instanceof EnumConstantElement) {
      return new PsiEnumConstantImpl(node);
    }
    else {
      return new PsiFieldImpl(node);
    }
  }

  @NotNull
  @Override
  public PsiFieldStub createStub(@NotNull final LighterAST tree, @NotNull final LighterASTNode node, @NotNull final StubElement parentStub) {
    final TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);

    boolean isDeprecatedByComment = false;
    boolean hasDeprecatedAnnotation = false;
    boolean hasDocComment = false;
    String name = null;
    String initializer = null;

    boolean expectingInit = false;
    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        hasDocComment = true;
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
      }
      else if (type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (type == JavaTokenType.EQ) {
        expectingInit = true;
      }
      else if (expectingInit && !ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(type) && type != JavaTokenType.SEMICOLON) {
        initializer = encodeInitializer(tree, child);
        break;
      }
    }

    final boolean isEnumConst = node.getTokenType() == JavaElementType.ENUM_CONSTANT;
    final byte flags = PsiFieldStubImpl.packFlags(isEnumConst, isDeprecatedByComment, hasDeprecatedAnnotation, hasDocComment);

    return new PsiFieldStubImpl(parentStub, name, typeInfo, initializer, flags);
  }

  private static String encodeInitializer(final LighterAST tree, final LighterASTNode initializer) {
    final IElementType type = initializer.getTokenType();
    if (type == JavaElementType.NEW_EXPRESSION || type == JavaElementType.METHOD_CALL_EXPRESSION) {
      return PsiFieldStub.INITIALIZER_NOT_STORED;
    }

    if (initializer.getEndOffset() - initializer.getStartOffset() > INITIALIZER_LENGTH_LIMIT) {
      return PsiFieldStub.INITIALIZER_TOO_LONG;
    }

    return LightTreeUtil.toFilteredString(tree, initializer, null);
  }

  @Override
  public void serialize(@NotNull PsiFieldStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getType());
    dataStream.writeName(stub.getInitializerText());
    dataStream.writeByte(((PsiFieldStubImpl)stub).getFlags());
  }

  @NotNull
  @Override
  public PsiFieldStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    String name = dataStream.readNameString();
    TypeInfo type = TypeInfo.readTYPE(dataStream);
    String initializerText = dataStream.readNameString();
    byte flags = dataStream.readByte();
    return new PsiFieldStubImpl(parentStub, name, type, initializerText, flags);
  }

  @Override
  public void indexStub(@NotNull PsiFieldStub stub, @NotNull IndexSink sink) {
    String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaStubIndexKeys.FIELDS, name);
      if (RecordUtil.isStaticNonPrivateMember(stub)) {
        sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_NAMES, name);
        sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, stub.getType().getShortTypeText());
      }
    }
  }

}