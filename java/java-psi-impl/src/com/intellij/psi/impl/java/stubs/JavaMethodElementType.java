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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.impl.PsiMethodStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.AnnotationMethodElement;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public abstract class JavaMethodElementType extends JavaStubElementType<PsiMethodStub, PsiMethod> {
  public static final String TYPE_PARAMETER_PSEUDO_NAME = "$TYPE_PARAMETER$";
  public JavaMethodElementType(@NonNls final String name) {
    super(name);
  }

  @Override
  public PsiMethod createPsi(@NotNull final PsiMethodStub stub) {
    return getPsiFactory(stub).createMethod(stub);
  }

  @Override
  public PsiMethod createPsi(@NotNull final ASTNode node) {
    if (node instanceof AnnotationMethodElement) {
      return new PsiAnnotationMethodImpl(node);
    }
    else {
      return new PsiMethodImpl(node);
    }
  }

  @Override
  public PsiMethodStub createStub(final LighterAST tree, final LighterASTNode node, final StubElement parentStub) {
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
            isVarArgs = (LightTreeUtil.firstChildOfType(tree, pType, JavaTokenType.ELLIPSIS) != null);
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
    boolean isAnno = (node.getTokenType() == JavaElementType.ANNOTATION_METHOD);
    byte flags = PsiMethodStubImpl.packFlags(isConstructor, isAnno, isVarArgs, isDeprecatedByComment, hasDeprecatedAnnotation, hasDocComment);

    return new PsiMethodStubImpl(parentStub, name, typeInfo, flags, defValueText);
  }

  @Override
  public void serialize(@NotNull final PsiMethodStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName());
    TypeInfo.writeTYPE(dataStream, stub.getReturnTypeText(false));
    dataStream.writeByte(((PsiMethodStubImpl)stub).getFlags());
    if (stub.isAnnotationMethod()) {
      dataStream.writeName(stub.getDefaultValueText());
    }
  }

  @NotNull
  @Override
  public PsiMethodStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    final TypeInfo type = TypeInfo.readTYPE(dataStream);
    byte flags = dataStream.readByte();
    final StringRef defaultMethodValue = PsiMethodStubImpl.isAnnotationMethod(flags) ? dataStream.readName() : null;
    return new PsiMethodStubImpl(parentStub, StringRef.toString(name), type, flags, StringRef.toString(defaultMethodValue));
  }

  @Override
  public void indexStub(@NotNull final PsiMethodStub stub, @NotNull final IndexSink sink) {
    final String name = stub.getName();
    if (name != null) {
      sink.occurrence(JavaStubIndexKeys.METHODS, name);
      if (RecordUtil.isStaticNonPrivateMember(stub)) {
        sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_NAMES, name);
        sink.occurrence(JavaStubIndexKeys.JVM_STATIC_MEMBERS_TYPES, stub.getReturnTypeText(false).getShortTypeText());
      }
    }

    Set<String> methodTypeParams = null;
    for (StubElement stubElement : stub.getChildrenStubs()) {
      if (stubElement instanceof PsiTypeParameterListStub) {
        for (Object tStub : stubElement.getChildrenStubs()) {
          if (tStub instanceof PsiTypeParameterStub) {
            if (methodTypeParams == null) {
              methodTypeParams = new HashSet<String>();
            }
            methodTypeParams.add(((PsiTypeParameterStub)tStub).getName());
          }
        }
      }
      else if (stubElement instanceof PsiParameterListStub) {
        for (StubElement paramStub : ((PsiParameterListStub)stubElement).getChildrenStubs()) {
          if (paramStub instanceof PsiParameterStub) {
            TypeInfo type = ((PsiParameterStub)paramStub).getType(false);
            if (type.arrayCount > 0) continue;
            String typeName = type.getShortTypeText();
            if (TypeConversionUtil.isPrimitive(typeName) || TypeConversionUtil.isPrimitiveWrapper(typeName)) continue;
            sink.occurrence(JavaStubIndexKeys.METHOD_TYPES, typeName);
            if (methodTypeParams != null && methodTypeParams.contains(typeName)) {
              sink.occurrence(JavaStubIndexKeys.METHOD_TYPES, TYPE_PARAMETER_PSEUDO_NAME);
              methodTypeParams = null;
            }
          }
        }
        break;
      }
    }
  }
}
