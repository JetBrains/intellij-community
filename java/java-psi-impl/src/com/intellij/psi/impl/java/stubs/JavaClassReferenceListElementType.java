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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.impl.java.stubs.impl.PsiClassReferenceListStubImpl;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.impl.source.PsiReferenceListImpl;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterExtendsBoundsListImpl;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author max
 */
public abstract class JavaClassReferenceListElementType extends JavaStubElementType<PsiClassReferenceListStub, PsiReferenceList> {
  public JavaClassReferenceListElementType(@NotNull @NonNls String id) {
    super(id, true);
  }

  @Override
  public PsiReferenceList createPsi(final PsiClassReferenceListStub stub) {
    return getPsiFactory(stub).createClassReferenceList(stub);
  }

  @Override
  public PsiReferenceList createPsi(final ASTNode node) {
    if (node.getElementType() == JavaStubElementTypes.EXTENDS_BOUND_LIST) {
      return new PsiTypeParameterExtendsBoundsListImpl(node);
    }
    
    return new PsiReferenceListImpl(node);
  }

  @Override
  public PsiClassReferenceListStub createStub(final LighterAST tree,
                                              final LighterASTNode node,
                                              final StubElement parentStub) {
    final JavaClassReferenceListElementType type = (JavaClassReferenceListElementType)node.getTokenType();
    return new PsiClassReferenceListStubImpl(type, parentStub, getTexts(tree, node), elementTypeToRole(type));
  }

  private static JavaClassReferenceListElementType roleToElementType(final PsiReferenceList.Role role) {
    switch (role) {
      case EXTENDS_BOUNDS_LIST:
        return JavaStubElementTypes.EXTENDS_BOUND_LIST;
      case EXTENDS_LIST:
        return JavaStubElementTypes.EXTENDS_LIST;
      case IMPLEMENTS_LIST:
        return JavaStubElementTypes.IMPLEMENTS_LIST;
      case THROWS_LIST:
        return JavaStubElementTypes.THROWS_LIST;
    }

    throw new RuntimeException("Unknown role: " + role);
  }

  private static PsiReferenceList.Role elementTypeToRole(final IElementType type) {
    if (type == JavaStubElementTypes.EXTENDS_BOUND_LIST) return PsiReferenceList.Role.EXTENDS_BOUNDS_LIST;
    else if (type == JavaStubElementTypes.EXTENDS_LIST) return PsiReferenceList.Role.EXTENDS_LIST;
    else if (type == JavaStubElementTypes.IMPLEMENTS_LIST) return PsiReferenceList.Role.IMPLEMENTS_LIST;
    else if (type == JavaStubElementTypes.THROWS_LIST) return PsiReferenceList.Role.THROWS_LIST;

    throw new RuntimeException("Unknown element type: " + type);
  }

  private static String[] getTexts(final LighterAST tree, final LighterASTNode node) {
    final List<LighterASTNode> refs = LightTreeUtil.getChildrenOfType(tree, node, JavaElementType.JAVA_CODE_REFERENCE);
    final String[] texts = ArrayUtil.newStringArray(refs.size());
    for (int i = 0; i < refs.size(); i++) {
      texts[i] = LightTreeUtil.toFilteredString(tree, refs.get(i), null);
    }
    return texts;
  }

  @Override
  public void serialize(final PsiClassReferenceListStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeByte(encodeRole(stub.getRole()));
    final String[] names = stub.getReferencedNames();
    dataStream.writeVarInt(names.length);
    for (String name : names) {
      dataStream.writeName(name);
    }
  }

  @Override
  public PsiClassReferenceListStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    byte role = dataStream.readByte();
    int len = dataStream.readVarInt();
    StringRef[] names = StringRef.createArray(len);
    for (int i = 0; i < names.length; i++) {
      names[i] = dataStream.readName();
    }

    final PsiReferenceList.Role decodedRole = decodeRole(role);
    return new PsiClassReferenceListStubImpl(roleToElementType(decodedRole), parentStub, names, decodedRole);
  }

  private static PsiReferenceList.Role decodeRole(int code) {
    switch (code) {
      case 0: return PsiReferenceList.Role.EXTENDS_LIST;
      case 1: return PsiReferenceList.Role.IMPLEMENTS_LIST;
      case 2: return PsiReferenceList.Role.THROWS_LIST;
      case 3: return PsiReferenceList.Role.EXTENDS_BOUNDS_LIST;

      default:
        throw new RuntimeException("Unknown role code: " + code);
    }
  }

  private static byte encodeRole(PsiReferenceList.Role role) {
    switch (role) {
      case EXTENDS_LIST:         return 0;
      case IMPLEMENTS_LIST:      return 1;
      case THROWS_LIST:          return 2;
      case EXTENDS_BOUNDS_LIST:  return 3;

      default:
        throw new RuntimeException("Unknown role code: " + role);
    }
  }

  @Override
  public void indexStub(final PsiClassReferenceListStub stub, final IndexSink sink) {
    final PsiReferenceList.Role role = stub.getRole();
    if (role == PsiReferenceList.Role.EXTENDS_LIST || role == PsiReferenceList.Role.IMPLEMENTS_LIST) {
      final String[] names = stub.getReferencedNames();
      for (String name : names) {
        sink.occurrence(JavaStubIndexKeys.SUPER_CLASSES, PsiNameHelper.getShortClassName(name));
      }

      if (role == PsiReferenceList.Role.EXTENDS_LIST) {
        StubElement parentStub = stub.getParentStub();
        if (parentStub instanceof PsiClassStub) {
          PsiClassStub psiClassStub = (PsiClassStub)parentStub;
          if (psiClassStub.isEnum()) {
            sink.occurrence(JavaStubIndexKeys.SUPER_CLASSES, "Enum");
          }

          if (psiClassStub.isAnnotationType()) {
            sink.occurrence(JavaStubIndexKeys.SUPER_CLASSES, "Annotation");
          }
        }
      }
    }
  }
}
