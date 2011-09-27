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
package com.intellij.psi.impl.cache;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.java.stubs.PsiClassStub;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.SmartList;
import com.intellij.util.io.StringRef;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class TypeInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.cache.TypeInfo");

  private static final TIntObjectHashMap<String> ourIndexFrequentType = new TIntObjectHashMap<String>();
  private static final TObjectIntHashMap<String> ourFrequentTypeIndex = new TObjectIntHashMap<String>();

  public final StringRef text;
  public final byte arrayCount;
  public final boolean isEllipsis;
  private final List<PsiAnnotationStub> myAnnotationStubs;

  private static void registerFrequentType(String typeText) {
    int index = ourFrequentTypeIndex.size() + 1;
    assert index < 15; // 0xf is reserved
    ourFrequentTypeIndex.put(typeText, index);
    ourIndexFrequentType.put(index, typeText);
  }

  static {
    registerFrequentType("boolean");
    registerFrequentType("byte");
    registerFrequentType("char");
    registerFrequentType("double");
    registerFrequentType("float");
    registerFrequentType("int");
    registerFrequentType("long");
    registerFrequentType("null");
    registerFrequentType("short");
    registerFrequentType("void");
    registerFrequentType("Object");
    registerFrequentType("java.lang.Object");
    registerFrequentType("String");
    registerFrequentType("java.lang.String");
  }

  public TypeInfo(StringRef text, byte arrayCount, boolean ellipsis, @NotNull List<PsiAnnotationStub> annotationStubs) {
    this.text = text;
    this.arrayCount = arrayCount;
    isEllipsis = ellipsis;
    myAnnotationStubs = annotationStubs;
  }

  public TypeInfo(@NotNull TypeInfo typeInfo) {
    this.text = typeInfo.text;
    this.arrayCount = typeInfo.arrayCount;
    isEllipsis = typeInfo.isEllipsis;
    myAnnotationStubs = new SmartList<PsiAnnotationStub>(typeInfo.myAnnotationStubs);
  }

  @NotNull
  public static TypeInfo createConstructorType() {
    return NULL;
  }

  @NotNull
  public static TypeInfo create(final LighterAST tree, final LighterASTNode element, final StubElement parentStub) {
    final String text;
    int arrayCount = 0;
    boolean isEllipsis = false;

    if (element.getTokenType() == JavaElementType.ENUM_CONSTANT) {
      text = ((PsiClassStub)parentStub).getName();
    }
    else {
      LighterASTNode typeElement = null;

      for (final LighterASTNode child : tree.getChildren(element)) {
        final IElementType type = child.getTokenType();
        if (type == JavaElementType.TYPE) {
          typeElement = child;
        }
        else if (type == JavaTokenType.LBRACKET) {
          arrayCount++;  // C-style array
        }
      }

      if (typeElement == null && element.getTokenType() == JavaElementType.FIELD) {
        final List<LighterASTNode> fields = LightTreeUtil.getChildrenOfType(tree, tree.getParent(element), JavaElementType.FIELD);
        final int idx = fields.indexOf(element);
        for (int i = idx - 1; i >= 0 && typeElement == null; i--) {  // int i, j
          typeElement = LightTreeUtil.firstChildOfType(tree, fields.get(i), JavaElementType.TYPE);
        }
      }

      assert typeElement != null : element + " in " + parentStub;

      isEllipsis = (LightTreeUtil.firstChildOfType(tree, typeElement, JavaTokenType.ELLIPSIS) != null);

      while (true) {
        final LighterASTNode nested = LightTreeUtil.firstChildOfType(tree, typeElement, JavaElementType.TYPE);
        if (nested == null) break;
        typeElement = nested;
        arrayCount++;  // Java-style array
      }

      text = LightTreeUtil.toFilteredString(tree, typeElement, null);
    }

    final List<PsiAnnotationStub> annotations = Collections.emptyList();  // todo[r.sh] JDK 8 type annotations

    return new TypeInfo(StringRef.fromString(text), (byte)arrayCount, isEllipsis, annotations);
  }

  @NotNull
  public static TypeInfo fromString(String typeText, boolean isEllipsis) {
    assert !typeText.endsWith("...") : typeText;

    byte arrayCount = 0;
    while (typeText.endsWith("[]")) {
      arrayCount++;
      typeText = typeText.substring(0, typeText.length() - 2);
    }

    StringRef text = StringRef.fromString(typeText);

    return new TypeInfo(text, arrayCount, isEllipsis, Collections.<PsiAnnotationStub>emptyList());
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

  private static final TypeInfo NULL = new TypeInfo(null, (byte)0, false, Collections.<PsiAnnotationStub>emptyList());
  
  @NotNull
  public static TypeInfo readTYPE(StubInputStream record, StubElement parentStub) throws IOException {
    final int flags = 0xFF & record.readByte();
    if (flags == NULL_FLAGS) {
      return NULL;
    }

    // flags bits
    // ZeroPad:1 IsEllipsis:1 ArrayCountPresent:1 AnnotationsArePresent:1 FrequentIndex:4

    final int frequentIndex = 0xF & flags;

    byte arrayCount = (flags & 0x20) != 0 ? record.readByte() : 0;
    boolean isEllipsis = (flags & 0x40) != 0;
    StringRef text;
    if (frequentIndex == 0) {
      text = record.readName();
    }
    else {
      text = StringRef.fromString(ourIndexFrequentType.get(frequentIndex));
    }
    boolean annotationsArePresent = (flags & 0x10) != 0;
    List<PsiAnnotationStub> annotationStubs;
    if (annotationsArePresent) {
      int size = 0xFF & record.readByte();
      annotationStubs = new ArrayList<PsiAnnotationStub>(size);
      for (int i =0; i<size; i++) {
        PsiAnnotationStub annotationStub = JavaStubElementTypes.ANNOTATION.deserialize(record, parentStub);
        annotationStubs.add(annotationStub);
      }
    }
    else {
      annotationStubs = Collections.emptyList();
    }
    return new TypeInfo(text, arrayCount, isEllipsis, annotationStubs);
  }

  private static final int NULL_FLAGS = 0x0F;
  public static void writeTYPE(final StubOutputStream dataStream, @NotNull TypeInfo typeInfo) throws IOException {
    if (typeInfo == NULL) {
      dataStream.writeByte(NULL_FLAGS);
      return;
    }

    boolean isEllipsis = typeInfo.isEllipsis;
    String text = typeInfo.text.getString();
    byte arrayCount = typeInfo.arrayCount;

    int frequentIndex = ourFrequentTypeIndex.get(text);
    LOG.assertTrue(frequentIndex < 15, frequentIndex);

    // flags bits
    // ZeroPad:1 IsEllipsis:1 ArrayCountPresent:1 AnnotationsArePresent:1 FrequentIndex:4

    List<PsiAnnotationStub> annotations = typeInfo.myAnnotationStubs;
    boolean annotationsArePresent = !annotations.isEmpty();
    int flags = ((isEllipsis ? 1 : 0) << 6) |
                  ((arrayCount == 0 ? 0 : 1) << 5) |
                      ((annotationsArePresent ? 1 : 0)<<4) |
                           frequentIndex;
    dataStream.writeByte(flags);
    if (arrayCount != 0) {
      dataStream.writeByte(arrayCount);
    }
    if (frequentIndex == 0) {
      dataStream.writeName(text);
    }
    if (annotationsArePresent) {
      LOG.assertTrue(annotations.size() < 256, annotations.size());
      dataStream.writeByte(annotations.size());
      for (PsiAnnotationStub annotation : annotations) {
        dataStream.writeUTFFast(annotation.getText());
      }
    }
  }

  @Nullable
  public static String createTypeText(@NotNull TypeInfo typeInfo) {
    if (typeInfo == NULL) return null;
    if (typeInfo.text == null) return null;
    if (typeInfo.arrayCount == 0 && typeInfo.myAnnotationStubs.isEmpty()) return typeInfo.text.getString();

    String text = typeInfo.text.getString();
    StringBuilder buf = new StringBuilder(text.length());
    for (PsiAnnotationStub stub : typeInfo.myAnnotationStubs) {
      buf.append(stub.getText()).append(" ");
    }
    buf.append(text);
    final int arrayCount = typeInfo.isEllipsis ? typeInfo.arrayCount - 1 : typeInfo.arrayCount;
    for (int i = 0; i < arrayCount; i++) buf.append("[]");
    if (typeInfo.isEllipsis) {
      buf.append("...");
    }

    return buf.toString();
  }

  public void addAnnotation(PsiAnnotationStub annotation) {
    myAnnotationStubs.add(annotation);
  }
}