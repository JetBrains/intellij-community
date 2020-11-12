// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiRecordComponentStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PsiRecordComponentStubImpl extends StubBase<PsiRecordComponent> implements PsiRecordComponentStub {
  private final static byte ELLIPSIS = 0x01;
  private final static byte HAS_DEPRECATED_ANNOTATION = 0x02;

  private final String myName;
  private final TypeInfo myType;
  private final byte myFlags;


  public PsiRecordComponentStubImpl(StubElement parent, @Nullable String name, @NotNull TypeInfo type, byte flags) {
    super(parent, JavaStubElementTypes.RECORD_COMPONENT);
    myName = name;
    myType = type;
    myFlags = flags;
  }

  public PsiRecordComponentStubImpl(StubElement parent,
                                    @Nullable String name,
                                    TypeInfo type,
                                    boolean isEllipsis,
                                    boolean hasDeprecatedAnnotation) {
    this(parent, name, type, packFlags(isEllipsis, hasDeprecatedAnnotation));
  }

  @Override
  @NotNull
  public TypeInfo getType() {
    return myType;
  }


  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isDeprecated() {
    return false;
  }

  @Override
  public boolean hasDeprecatedAnnotation() {
    return BitUtil.isSet(myFlags, HAS_DEPRECATED_ANNOTATION);
  }

  @Override
  public boolean hasDocComment() {
    return false;
  }

  @Override
  public boolean isVararg() {
    return BitUtil.isSet(myFlags, ELLIPSIS);
  }

  public byte getFlags() {
    return myFlags;
  }

  public static byte packFlags(boolean isEllipsis, boolean hasDeprecatedAnnotation) {
    byte flags = 0;
    flags = BitUtil.set(flags, ELLIPSIS, isEllipsis);
    flags = BitUtil.set(flags, HAS_DEPRECATED_ANNOTATION, hasDeprecatedAnnotation);
    return flags;
  }


  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiRecordComponentStub[");

    if (hasDeprecatedAnnotation()) {
      builder.append("deprecated ");
    }

    builder.append(myName).append(':').append(myType);

    builder.append(']');
    return builder.toString();
  }
}