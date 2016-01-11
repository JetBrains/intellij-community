/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.impl.source.PsiJavaCodeReferenceElementImpl;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class PsiImportStatementStubImpl extends StubBase<PsiImportStatementBase> implements PsiImportStatementStub {
  private final byte myFlags;
  private final StringRef myText;
  private SoftReference<PsiJavaCodeReferenceElement> myReference = null;

  private static final int ON_DEMAND = 0x01;
  private static final int STATIC = 0x02;

  public PsiImportStatementStubImpl(final StubElement parent, final String text, final byte flags) {
    this(parent, StringRef.fromString(text), flags);
  }

  public PsiImportStatementStubImpl(final StubElement parent, final StringRef text, final byte flags) {
    super(parent, isStatic(flags) ? JavaStubElementTypes.IMPORT_STATIC_STATEMENT : JavaStubElementTypes.IMPORT_STATEMENT);
    myText = text;
    myFlags = flags;
  }

  @Override
  public boolean isStatic() {
    return isStatic(myFlags);
  }

  private static boolean isStatic(final byte flags) {
    return (flags & STATIC) != 0;
  }

  @Override
  public boolean isOnDemand() {
    return (myFlags & ON_DEMAND) != 0;
  }

  public byte getFlags() {
    return myFlags;
  }

  @Override
  public String getImportReferenceText() {
    return StringRef.toString(myText);
  }

  @Override
  @Nullable
  public PsiJavaCodeReferenceElement getReference() {
    PsiJavaCodeReferenceElement ref = SoftReference.dereference(myReference);
    if (ref == null) {
      ref = isStatic() ? getStaticReference() : getRegularReference();
      myReference = new SoftReference<PsiJavaCodeReferenceElement>(ref);
    }
    return ref;
  }

  public static byte packFlags(boolean isOnDemand, boolean isStatic) {
    byte flags = 0;
    if (isOnDemand) {
      flags |= ON_DEMAND;
    }
    if (isStatic) {
      flags |= STATIC;
    }
    return flags;
  }

  @Nullable
  private PsiJavaCodeReferenceElement getStaticReference() {
    final PsiJavaCodeReferenceElement refElement = createReference();
    if (refElement == null) return null;
    if (isOnDemand() && refElement instanceof PsiJavaCodeReferenceElementImpl) {
      ((PsiJavaCodeReferenceElementImpl)refElement).setKindWhenDummy(PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);
    }
    return refElement;
  }

  @Nullable
  private PsiJavaCodeReferenceElement getRegularReference() {
    final PsiJavaCodeReferenceElement refElement = createReference();
    if (refElement == null) return null;
    ((PsiJavaCodeReferenceElementImpl)refElement).setKindWhenDummy(
      isOnDemand() ? PsiJavaCodeReferenceElementImpl.CLASS_FQ_OR_PACKAGE_NAME_KIND
                   : PsiJavaCodeReferenceElementImpl.CLASS_FQ_NAME_KIND);
    return refElement;
  }

  @Nullable
  private PsiJavaCodeReferenceElement createReference() {
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

    builder.append(getImportReferenceText());

    if (isOnDemand()) {
      builder.append(".*");
    }

    builder.append("]");
    return builder.toString();
  }
}
