// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiImportModuleStatementStub;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.openapi.util.text.StringUtil.nullize;
import static com.intellij.reference.SoftReference.dereference;

public class PsiImportModuleStatementImpl extends JavaStubPsiElement<PsiImportModuleStatementStub> implements PsiImportModuleStatement {
  public static final PsiImportModuleStatementImpl[] EMPTY_ARRAY = new PsiImportModuleStatementImpl[0];
  public static final ArrayFactory<PsiImportModuleStatementImpl> ARRAY_FACTORY =
    count -> count == 0 ? EMPTY_ARRAY : new PsiImportModuleStatementImpl[count];

  private SoftReference<PsiJavaModuleReference> myReference;

  public PsiImportModuleStatementImpl(PsiImportModuleStatementStub stub) {
    super(stub, JavaStubElementTypes.IMPORT_MODULE_STATEMENT);
  }

  public PsiImportModuleStatementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiJavaModule resolveTargetModule() {
    PsiJavaModuleReference moduleReference = getModuleReference();
    if (moduleReference == null) return null;
    return moduleReference.resolve();
  }

  @Override
  public String getReferenceName() {
    PsiJavaModuleReference moduleReference = getModuleReference();
    if (moduleReference == null) return null;
    return moduleReference.getCanonicalText();
  }

  @Override
  public @Nullable PsiJavaModuleReference getModuleReference() {
    PsiImportModuleStatementStub stub = getStub();
    if (stub != null) {
      String refText = nullize(stub.getImportReferenceText());
      if (refText == null) return null;
      PsiJavaModuleReference ref = dereference(myReference);
      if (ref == null) {
        ref = JavaPsiFacade.getInstance(getProject()).getParserFacade().createModuleReferenceFromText(refText, this).getReference();
        myReference = new SoftReference<>(ref);
      }
      return ref;
    }
    else {
      myReference = null;
      PsiJavaModuleReferenceElement refElement = PsiTreeUtil.getChildOfType(this, PsiJavaModuleReferenceElement.class);
      return refElement != null ? refElement.getReference() : null;
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
    PsiJavaModuleReference ref = getModuleReference();
    return ref != null ? ref.resolve() : null;
  }

  @Override
  public String toString() {
    return "PsiImportModuleStatement";
  }
}