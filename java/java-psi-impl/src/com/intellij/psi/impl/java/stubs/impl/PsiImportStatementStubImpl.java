// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaImportStatementElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.BitUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class PsiImportStatementStubImpl extends StubBase<PsiImportStatementBase> implements PsiImportStatementStub {
  private final byte myFlags;
  private final String myText;
  private SoftReference<PsiJavaCodeReferenceElement> myReference;
  private SoftReference<PsiJavaModuleReferenceElement> myModuleReference;

  private static final int ON_DEMAND = 0x01;
  private static final int STATIC = 0x02;
  private static final int MODULE = 0x04;

  public PsiImportStatementStubImpl(final StubElement parent, final String text, final byte flags) {
    super(parent, getImportType(flags));
    myText = text;
    myFlags = flags;
  }

  private static @NotNull JavaImportStatementElementType getImportType(final byte flags) {
    if (isStatic(flags)) return JavaStubElementTypes.IMPORT_STATIC_STATEMENT;
    if (isModule(flags)) return JavaStubElementTypes.IMPORT_MODULE_STATEMENT;
    return JavaStubElementTypes.IMPORT_STATEMENT;
  }

  @Override
  public boolean isStatic() {
    return isStatic(myFlags);
  }

  private static boolean isStatic(final byte flags) {
    return BitUtil.isSet(flags, STATIC);
  }

  @Override
  public boolean isModule() {
    return isModule(myFlags);
  }

  private static boolean isModule(final byte flags) {
    return BitUtil.isSet(flags, MODULE);
  }

  @Override
  public boolean isOnDemand() {
    return BitUtil.isSet(myFlags, ON_DEMAND);
  }

  public byte getFlags() {
    return myFlags;
  }

  @Override
  public String getImportReferenceText() {
    return myText;
  }

  @Override
  public @Nullable PsiJavaCodeReferenceElement getReference() {
    PsiJavaCodeReferenceElement ref = dereference(myReference);
    if (ref == null && !isModule()) {
      ref = isStatic() ? getStaticReference() : getRegularReference();
      myReference = new SoftReference<>(ref);
    }
    return ref;
  }

  @Override
  public @Nullable PsiJavaModuleReferenceElement getModuleReference() {
    if (!isModule()) return null;
    PsiJavaModuleReferenceElement ref = dereference(myModuleReference);
    if (ref == null) {
      ref = createModuleReference();
      myModuleReference = new SoftReference<>(ref);
    }
    return ref;
  }

  public static byte packFlags(boolean isOnDemand, boolean isStatic, boolean isModule) {
    byte flags = 0;
    if (isOnDemand) {
      flags |= ON_DEMAND;
    }
    if (isStatic) {
      flags |= STATIC;
    }
    if (isModule) {
      flags |= MODULE;
    }
    return flags;
  }

  private @Nullable PsiJavaCodeReferenceElement getStaticReference() {
    final PsiJavaCodeReferenceElement refElement = createReference();
    if (refElement == null) return null;
    if (isOnDemand() && refElement instanceof PsiJavaCodeReferenceElementImpl) {
      ((PsiJavaCodeReferenceElementImpl)refElement).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_NAME_KIND);
    }
    return refElement;
  }

  private @Nullable PsiJavaCodeReferenceElement getRegularReference() {
    final PsiJavaCodeReferenceElement refElement = createReference();
    if (refElement == null) return null;
    ((PsiJavaCodeReferenceElementImpl)refElement).setKindWhenDummy(
      isOnDemand() ? PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_OR_PACKAGE_NAME_KIND
                   : PsiJavaCodeReferenceElementImpl.Kind.CLASS_FQ_NAME_KIND);
    return refElement;
  }

  private @Nullable PsiJavaModuleReferenceElement createModuleReference() {
    final String refText = getImportReferenceText();
    if (refText == null) return null;
    try {
      final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
      return parserFacade.createModuleReferenceFromText(refText, getPsi());
    }
    catch (IncorrectOperationException ignore) {
      return null;
    }
  }

  private @Nullable PsiJavaCodeReferenceElement createReference() {
    final String refText = getImportReferenceText();
    if (refText == null) return null;

    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    try {
      return parserFacade.createReferenceFromText(refText, getPsi());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("PsiImportStatementStub[");

    if (isStatic()) {
      builder.append("static ");
    }
    if (isModule()) {
      builder.append("module ");
    }

    builder.append(getImportReferenceText());

    if (isOnDemand() && !isModule()) {
      builder.append(".*");
    }

    builder.append("]");
    return builder.toString();
  }
}
