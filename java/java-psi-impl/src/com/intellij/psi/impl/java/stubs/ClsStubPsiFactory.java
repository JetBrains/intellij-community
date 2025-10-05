// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.*;
import com.intellij.psi.impl.compiled.*;
import com.intellij.psi.impl.java.stubs.impl.PsiClassStubImpl;

public class ClsStubPsiFactory extends StubPsiFactory {
  public static final ClsStubPsiFactory INSTANCE = new ClsStubPsiFactory();

  @Override
  public PsiClass createClass(PsiClassStub stub) {
    boolean anonymous = stub instanceof PsiClassStubImpl && ((PsiClassStubImpl<?>)stub).isAnonymousInner();
    return anonymous ? new ClsAnonymousClass(stub) : new ClsClassImpl(stub);
  }

  @Override
  public PsiAnnotation createAnnotation(PsiAnnotationStub stub) {
    return new ClsAnnotationImpl(stub);
  }

  @Override
  public PsiClassInitializer createClassInitializer(PsiClassInitializerStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiReferenceList createClassReferenceList(PsiClassReferenceListStub stub) {
    return new ClsReferenceListImpl(stub);
  }

  @Override
  public PsiField createField(PsiFieldStub stub) {
    return stub.isEnumConstant() ? new ClsEnumConstantImpl(stub) : new ClsFieldImpl(stub);
  }

  @Override
  public PsiImportList createImportList(PsiImportListStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiImportStatementBase createImportStatement(PsiImportStatementStub stub) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiMethod createMethod(PsiMethodStub stub) {
    return new ClsMethodImpl(stub);
  }

  @Override
  public PsiModifierList createModifierList(PsiModifierListStub stub) {
    return new ClsModifierListImpl(stub);
  }

  @Override
  public PsiParameter createParameter(PsiParameterStub stub) {
    return new ClsParameterImpl(stub);
  }

  @Override
  public PsiParameterList createParameterList(PsiParameterListStub stub) {
    return new ClsParameterListImpl(stub);
  }

  @Override
  public PsiTypeParameter createTypeParameter(PsiTypeParameterStub stub) {
    return new ClsTypeParameterImpl(stub);
  }

  @Override
  public PsiTypeParameterList createTypeParameterList(PsiTypeParameterListStub stub) {
    return new ClsTypeParametersListImpl(stub);
  }

  @Override
  public PsiAnnotationParameterList createAnnotationParameterList(PsiAnnotationParameterListStub stub) {
    return null; // todo
  }

  @Override
  public PsiNameValuePair createNameValuePair(PsiNameValuePairStub stub) {
    return null; // todo
  }

  @Override
  public PsiJavaModule createModule(PsiJavaModuleStub stub) {
    return new ClsJavaModuleImpl(stub);
  }

  @Override
  public PsiRequiresStatement createRequiresStatement(PsiRequiresStatementStub stub) {
    return new ClsRequiresStatementImpl(stub);
  }

  @Override
  public PsiPackageAccessibilityStatement createPackageAccessibilityStatement(PsiPackageAccessibilityStatementStub stub) {
    return new ClsPackageAccessibilityStatementImpl(stub);
  }

  @Override
  public PsiPackageStatement createPackageStatement(PsiPackageStatementStub stub) {
    return new ClsPackageStatementImpl(stub);
  }
  
  @Override
  public PsiUsesStatement createUsesStatement(PsiUsesStatementStub stub) {
    return new ClsUsesStatementImpl(stub);
  }

  @Override
  public PsiProvidesStatement createProvidesStatement(PsiProvidesStatementStub stub) {
    return new ClsProvidesStatementImpl(stub);
  }

  @Override
  public PsiRecordComponent createRecordComponent(PsiRecordComponentStub stub) {
    return new ClsRecordComponentImpl(stub);
  }

  @Override
  public PsiRecordHeader createRecordHeader(PsiRecordHeaderStub stub) {
    return new ClsRecordHeaderImpl(stub);
  }
}