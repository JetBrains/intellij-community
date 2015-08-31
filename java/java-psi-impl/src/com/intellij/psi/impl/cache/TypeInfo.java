/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.java.stubs.PsiModifierListStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.io.StringRef;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

import static com.intellij.util.BitUtil.isSet;

/**
 * @author max
 */
public class TypeInfo {
  private static final String[] ourIndexFrequentType;
  private static final TObjectIntHashMap<String> ourFrequentTypeIndex;

  static {
    ourIndexFrequentType = new String[]{
      "",
      "boolean",
      "byte",
      "char",
      "double",
      "float",
      "int",
      "long",
      "null",
      "short",
      "void",
      CommonClassNames.JAVA_LANG_OBJECT_SHORT,
      CommonClassNames.JAVA_LANG_OBJECT,
      CommonClassNames.JAVA_LANG_STRING_SHORT,
      CommonClassNames.JAVA_LANG_STRING
    };

    ourFrequentTypeIndex = new TObjectIntHashMap<String>();
    for (int i = 0; i < ourIndexFrequentType.length; i++) {
      String type = ourIndexFrequentType[i];
      ourFrequentTypeIndex.put(type, i);
    }
  }

  private static final int FREQUENT_INDEX_MASK = 0x03F;
  private static final int HAS_ARRAY_COUNT = 0x40;
  private static final int HAS_ELLIPSIS = 0x80;

  private static final TypeInfo NULL = new TypeInfo(null, (byte)0, false, PsiAnnotationStub.EMPTY_ARRAY);

  public final String text;
  public final byte arrayCount;
  public final boolean isEllipsis;

  private final PsiAnnotationStub[] myAnnotationStubs;

  public TypeInfo(String text, byte arrayCount, boolean ellipsis, @NotNull PsiAnnotationStub[] annotationStubs) {
    this.text = text == null ? null : internFrequentType(text);
    this.arrayCount = arrayCount;
    this.isEllipsis = ellipsis;
    myAnnotationStubs = annotationStubs;
  }

  @NotNull
  public TypeInfo applyAnnotations(@NotNull StubBase<?> owner) {
    PsiModifierListStub modifierList = (PsiModifierListStub)owner.findChildStubByType(JavaStubElementTypes.MODIFIER_LIST);
    if (modifierList == null) return this;

    List<PsiAnnotationStub> annotationStubs = null;
    for (StubElement child : modifierList.getChildrenStubs()) {
      if (!(child instanceof PsiAnnotationStub)) continue;
      PsiAnnotationStub annotationStub = (PsiAnnotationStub)child;
      if (PsiImplUtil.isTypeAnnotation(annotationStub.getPsiElement())) {
        if (annotationStubs == null) annotationStubs = new SmartList<PsiAnnotationStub>();
        annotationStubs.add(annotationStub);
      }
    }

    PsiAnnotationStub[] stubArray = PsiAnnotationStub.EMPTY_ARRAY;
    if (annotationStubs != null) stubArray = annotationStubs.toArray(new PsiAnnotationStub[annotationStubs.size()]);
    return new TypeInfo(text, arrayCount, isEllipsis, stubArray);
  }

  @NotNull
  public String getShortTypeText() {
    if (text == null) return "";
    String name = PsiNameHelper.getShortClassName(text);
    if (arrayCount > 0) {
      name += StringUtil.repeat("[]", arrayCount);
    }
    return name;
  }

  @Override
  public String toString() {
    String text = createTypeText(this);
    return text != null ? text : "null";
  }

  /* factories and serialization */

  @NotNull
  public static TypeInfo createConstructorType() {
    return NULL;
  }

  @NotNull
  public static TypeInfo create(@NotNull LighterAST tree, @NotNull LighterASTNode element, StubElement parentStub) {
    String text;
    byte arrayCount = 0;
    boolean isEllipsis = false;

    if (element.getTokenType() == JavaElementType.ENUM_CONSTANT) {
      text = ((PsiClassStub)parentStub).getName();
    }
    else {
      LighterASTNode typeElement = null;

      for (final LighterASTNode child : tree.getChildren(element)) {
        IElementType type = child.getTokenType();
        if (type == JavaElementType.TYPE) {
          typeElement = child;
        }
        else if (type == JavaTokenType.LBRACKET) {
          arrayCount++;  // C-style array
        }
      }

      if (typeElement == null && element.getTokenType() == JavaElementType.FIELD) {
        LighterASTNode parent = tree.getParent(element);
        assert parent != null : element;
        List<LighterASTNode> fields = LightTreeUtil.getChildrenOfType(tree, parent, JavaElementType.FIELD);
        int idx = fields.indexOf(element);
        for (int i = idx - 1; i >= 0 && typeElement == null; i--) {  // int i, j
          typeElement = LightTreeUtil.firstChildOfType(tree, fields.get(i), JavaElementType.TYPE);
        }
      }

      assert typeElement != null : element + " in " + parentStub;

      isEllipsis = LightTreeUtil.firstChildOfType(tree, typeElement, JavaTokenType.ELLIPSIS) != null;

      while (true) {
        LighterASTNode nested = LightTreeUtil.firstChildOfType(tree, typeElement, JavaElementType.TYPE);
        if (nested == null) break;
        typeElement = nested;
        arrayCount++;  // Java-style array
      }

      text = LightTreeUtil.toFilteredString(tree, typeElement, null);
    }

    return new TypeInfo(text, arrayCount, isEllipsis, PsiAnnotationStub.EMPTY_ARRAY);
  }

  @NotNull
  public static TypeInfo fromString(@NotNull String typeText, boolean isEllipsis) {
    assert !typeText.endsWith("...") : typeText;

    byte arrayCount = 0;
    while (typeText.endsWith("[]")) {
      arrayCount++;
      typeText = typeText.substring(0, typeText.length() - 2);
    }

    return new TypeInfo(typeText, arrayCount, isEllipsis, PsiAnnotationStub.EMPTY_ARRAY);
  }

  @NotNull
  public static TypeInfo fromString(@NotNull String typeText) {
    boolean isEllipsis = false;
    if (typeText.endsWith("...")) {
      isEllipsis = true;
      typeText = typeText.substring(0, typeText.length() - 3);
    }

    return fromString(typeText, isEllipsis);
  }

  @NotNull
  public static TypeInfo readTYPE(@NotNull StubInputStream record) throws IOException {
    int flags = 0xFF & record.readByte();
    if (flags == FREQUENT_INDEX_MASK) {
      return NULL;
    }

    int frequentIndex = FREQUENT_INDEX_MASK & flags;
    byte arrayCount = isSet(flags, HAS_ARRAY_COUNT) ? record.readByte() : 0;
    boolean hasEllipsis = isSet(flags, HAS_ELLIPSIS);

    String text = frequentIndex == 0 ? StringRef.toString(record.readName()) : ourIndexFrequentType[frequentIndex];

    return new TypeInfo(text, arrayCount, hasEllipsis, PsiAnnotationStub.EMPTY_ARRAY);
  }

  public static void writeTYPE(@NotNull StubOutputStream dataStream, @NotNull TypeInfo typeInfo) throws IOException {
    if (typeInfo == NULL) {
      dataStream.writeByte(FREQUENT_INDEX_MASK);
      return;
    }

    String text = typeInfo.text;
    byte arrayCount = typeInfo.arrayCount;
    int frequentIndex = ourFrequentTypeIndex.get(text);
    int flags = (typeInfo.isEllipsis ? HAS_ELLIPSIS : 0) | (arrayCount != 0 ? HAS_ARRAY_COUNT : 0) | frequentIndex;
    dataStream.writeByte(flags);

    if (arrayCount != 0) {
      dataStream.writeByte(arrayCount);
    }

    if (frequentIndex == 0) {
      dataStream.writeName(text);
    }
  }

  @Nullable
  public static String createTypeText(@NotNull TypeInfo typeInfo) {
    if (typeInfo == NULL || typeInfo.text == null) {
      return null;
    }
    if (typeInfo.arrayCount == 0 && typeInfo.myAnnotationStubs.length == 0) {
      return typeInfo.text;
    }

    StringBuilder buf = new StringBuilder();

    for (PsiAnnotationStub stub : typeInfo.myAnnotationStubs) {
      buf.append(stub.getText()).append(' ');
    }

    buf.append(typeInfo.text);

    int arrayCount = typeInfo.isEllipsis ? typeInfo.arrayCount - 1 : typeInfo.arrayCount;
    for (int i = 0; i < arrayCount; i++) {
      buf.append("[]");
    }
    if (typeInfo.isEllipsis) {
      buf.append("...");
    }

    return internFrequentType(buf.toString());
  }

  @NotNull
  public static String internFrequentType(@NotNull String type) {
    int frequentIndex = ourFrequentTypeIndex.get(type);
    return frequentIndex == 0 ? type : ourIndexFrequentType[frequentIndex];
  }
}
