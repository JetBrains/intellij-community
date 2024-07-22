// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.reference.SoftReference.dereference;

public class PsiImportModuleStatementImpl extends PsiImportStatementBaseImpl implements PsiImportModuleStatement {
  public static final PsiImportModuleStatementImpl[] EMPTY_ARRAY = new PsiImportModuleStatementImpl[0];
  public static final ArrayFactory<PsiImportModuleStatementImpl> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportModuleStatementImpl[count];

  private SoftReference<PsiJavaModuleReferenceElement> myRefElement;

  public PsiImportModuleStatementImpl(PsiImportStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_MODULE_STATEMENT);
  }

  public PsiImportModuleStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiJavaModule resolveTargetModule() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    if (ref == null) return null;
    return ref.resolve();
  }

  @Override
  public String getReferenceName() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    if (ref == null) return null;
    return ref.getCanonicalText();
  }

  @Override
  public @Nullable PsiJavaModuleReferenceElement getModuleReference() {
    PsiImportStatementStub stub = getStub();
    if (stub != null) {
      String refText = nullize(stub.getImportReferenceText());
      if (refText == null) return null;
      PsiJavaModuleReferenceElement refElement = dereference(myRefElement);
      if (refElement == null) {
        refElement = JavaPsiFacade.getInstance(getProject()).getParserFacade().createModuleReferenceFromText(refText, this);
        myRefElement = new SoftReference<>(refElement);
      }
      return refElement;
    }
    else {
      myRefElement = null;
      return PsiTreeUtil.getChildOfType(this, PsiJavaModuleReferenceElement.class);
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitImportModuleStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiElement resolve() {
    PsiJavaModuleReferenceElement refElement = getModuleReference();
    if (refElement == null) return null;
    PsiJavaModuleReference ref = refElement.getReference();
    return ref != null ? ref.resolve() : null;
  }

  @Override
  public String toString() {
    return "PsiImportModuleStatement";
  }
}