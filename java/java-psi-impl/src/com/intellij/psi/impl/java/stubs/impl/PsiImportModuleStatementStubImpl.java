// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiJavaModuleReferenceElement;
import com.intellij.psi.PsiJavaParserFacade;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportModuleStatementStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public class PsiImportModuleStatementStubImpl extends StubBase<PsiImportModuleStatement> implements PsiImportModuleStatementStub {
  private final String myText;
  private SoftReference<PsiJavaModuleReferenceElement> myReference;

  public PsiImportModuleStatementStubImpl(final StubElement parent, final String text) {
    super(parent, JavaStubElementTypes.IMPORT_MODULE_STATEMENT);
    myText = text;
  }

  @Override
  public String getImportReferenceText() {
    return myText;
  }

  @Override
  public @Nullable PsiJavaModuleReferenceElement getReference() {
    PsiJavaModuleReferenceElement ref = dereference(myReference);
    if (ref == null) {
      ref = createReference();
      myReference = new SoftReference<>(ref);
    }
    return ref;
  }

  private @Nullable PsiJavaModuleReferenceElement createReference() {
    final String refText = getImportReferenceText();
    if (refText == null) return null;

    final PsiJavaParserFacade parserFacade = JavaPsiFacade.getInstance(getProject()).getParserFacade();
    try {
      return parserFacade.createModuleReferenceFromText(refText, getPsi());
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return "PsiImportModuleStatementStub[" + getImportReferenceText() + "]";
  }
}
