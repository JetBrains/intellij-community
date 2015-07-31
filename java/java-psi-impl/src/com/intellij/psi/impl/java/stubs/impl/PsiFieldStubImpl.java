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
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class PsiFieldStubImpl extends StubBase<PsiField> implements PsiFieldStub {
  private final StringRef myName;
  private final TypeInfo myType;
  private final StringRef myInitializer;
  private final byte myFlags;

  private static final int ENUM_CONST = 0x01;
  private static final int DEPRECATED = 0x02;
  private static final int DEPRECATED_ANNOTATION = 0x04;
  private static final int HAS_DOC_COMMENT = 0x08;

  public PsiFieldStubImpl(StubElement parent, String name, @NotNull TypeInfo type, @Nullable String initializer, byte flags) {
    this(parent, StringRef.fromString(name), type, StringRef.fromString(initializer), flags);
  }

  public PsiFieldStubImpl(StubElement parent, StringRef name, @NotNull TypeInfo type, @Nullable StringRef initializer, byte flags) {
    super(parent, isEnumConst(flags) ? JavaStubElementTypes.ENUM_CONSTANT : JavaStubElementTypes.FIELD);
    myName = name;
    myType = type;
    myInitializer = initializer;
    myFlags = flags;
  }

  @Override
  @NotNull
  public TypeInfo getType(boolean doResolve) {
    return doResolve ? myType.applyAnnotations(this) : myType;
  }

  @Override
  public String getInitializerText() {
    return StringRef.toString(myInitializer);
  }

  public byte getFlags() {
    return myFlags;
  }

  @Override
  public boolean isEnumConstant() {
    return isEnumConst(myFlags);
  }

  private static boolean isEnumConst(final byte flags) {
    return (flags & ENUM_CONST) != 0;
  }

  @Override
  public boolean isDeprecated() {
    return (myFlags & DEPRECATED) != 0;
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return (myFlags & DEPRECATED_ANNOTATION) != 0;
  }

  @Override
  public boolean hasDocComment() {
    return (myFlags & HAS_DOC_COMMENT) != 0;
  }

  @Override
  public String getName() {
    return StringRef.toString(myName);
  }

  public static byte packFlags(boolean isEnumConst, boolean isDeprecated, boolean hasDeprecatedAnnotation, boolean hasDocComment) {
    byte flags = 0;
    if (isEnumConst) flags |= ENUM_CONST;
    if (isDeprecated) flags |= DEPRECATED;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    if (hasDocComment) flags |= HAS_DOC_COMMENT;
    return flags;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiFieldStub[");

    if (isDeprecated() || hasDeprecatedAnnotation()) {
      builder.append("deprecated ");
    }
    if (isEnumConstant()) {
      builder.append("enumconst ");
    }

    builder.append(myName).append(':').append(myType);

    if (myInitializer != null) {
      builder.append('=').append(myInitializer);
    }

    builder.append("]");
    return builder.toString();
  }
}
