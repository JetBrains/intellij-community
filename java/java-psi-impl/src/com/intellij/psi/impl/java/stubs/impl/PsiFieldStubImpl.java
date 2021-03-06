// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiFieldStubImpl extends StubBase<PsiField> implements PsiFieldStub {
  private static final byte ENUM_CONST = 0x01;
  private static final byte DEPRECATED = 0x02;
  private static final byte DEPRECATED_ANNOTATION = 0x04;
  private static final byte HAS_DOC_COMMENT = 0x08;

  private final String myName;
  private final TypeInfo myType;
  private final String myInitializer;
  private final byte myFlags;

  public PsiFieldStubImpl(StubElement parent, @Nullable String name, @NotNull TypeInfo type, @Nullable String initializer, byte flags) {
    super(parent, isEnumConst(flags) ? JavaStubElementTypes.ENUM_CONSTANT : JavaStubElementTypes.FIELD);
    myName = name;
    myType = type;
    myInitializer = initializer;
    myFlags = flags;
  }

  public byte getFlags() {
    return myFlags;
  }

  @Override
  @NotNull
  public TypeInfo getType() {
    return myType;
  }

  @Override
  public String getInitializerText() {
    return myInitializer;
  }

  @Override
  public boolean isEnumConstant() {
    return isEnumConst(myFlags);
  }

  private static boolean isEnumConst(final byte flags) {
    return BitUtil.isSet(flags, ENUM_CONST);
  }

  @Override
  public boolean isDeprecated() {
    return BitUtil.isSet(myFlags, DEPRECATED);
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return BitUtil.isSet(myFlags, DEPRECATED_ANNOTATION);
  }

  @Override
  public boolean hasDocComment() {
    return BitUtil.isSet(myFlags, HAS_DOC_COMMENT);
  }

  @Override
  public String getName() {
    return myName;
  }

  public static byte packFlags(boolean isEnumConst, boolean isDeprecated, boolean hasDeprecatedAnnotation, boolean hasDocComment) {
    byte flags = 0;
    if (isEnumConst) flags |= ENUM_CONST;
    if (isDeprecated) flags |= DEPRECATED;
    if (hasDeprecatedAnnotation) flags |= DEPRECATED_ANNOTATION;
    if (hasDocComment) flags |= HAS_DOC_COMMENT;
    return flags;
  }

  @Override
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

    builder.append(']');
    return builder.toString();
  }
}