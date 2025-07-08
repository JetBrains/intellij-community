// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.java.*;

public class SourceStubPsiFactory extends StubPsiFactory {
  public static final SourceStubPsiFactory INSTANCE = new SourceStubPsiFactory();

  @Override
  public PsiClass createClass(PsiClassStub stub) {
    if (stub.isEnumConstantInitializer()) return new PsiEnumConstantInitializerImpl(stub);
    if (stub.isAnonymous()) return new PsiAnonymousClassImpl(stub);
    if (stub.isImplicit()) return new PsiImplicitClassImpl(stub);
    return new PsiClassImpl(stub);
  }

  @Override
  public PsiAnnotation createAnnotation(PsiAnnotationStub stub) {
    return new PsiAnnotationImpl(stub);
  }

  @Override
  public PsiClassInitializer createClassInitializer(PsiClassInitializerStub stub) {
    return new PsiClassInitializerImpl(stub);
  }

  @Override
  public PsiReferenceList createClassReferenceList(PsiClassReferenceListStub stub) {
    return new PsiReferenceListImpl(stub);
  }

  @Override
  public PsiField createField(PsiFieldStub stub) {
    return stub.isEnumConstant() ? new PsiEnumConstantImpl(stub) : new PsiFieldImpl(stub);
  }

  @Override
  public PsiImportList createImportList(PsiImportListStub stub) {
    return new PsiImportListImpl(stub);
  }

  @Override
  public PsiImportStatementBase createImportStatement(PsiImportStatementStub stub) {
    if (stub.isStatic()) {
      return new PsiImportStaticStatementImpl(stub);
    }
    else if (stub.isModule()) {
      return new PsiImportModuleStatementImpl(stub);
    }
    else {
      return new PsiImportStatementImpl(stub);
    }
  }

  @Override
  public PsiMethod createMethod(PsiMethodStub stub) {
    return stub.isAnnotationMethod() ? new PsiAnnotationMethodImpl(stub) : new PsiMethodImpl(stub);
  }

  @Override
  public PsiModifierList createModifierList(PsiModifierListStub stub) {
    return new PsiModifierListImpl(stub);
  }
  
  @Override
  public PsiPackageStatement createPackageStatement(PsiPackageStatementStub stub) {
    return new PsiPackageStatementImpl(stub);
  }

  @Override
  public PsiParameter createParameter(PsiParameterStub stub) {
    return new PsiParameterImpl(stub);
  }

  @Override
  public PsiParameterList createParameterList(PsiParameterListStub stub) {
    return new PsiParameterListImpl(stub);
  }

  @Override
  public PsiTypeParameter createTypeParameter(PsiTypeParameterStub stub) {
    return new PsiTypeParameterImpl(stub);
  }

  @Override
  public PsiTypeParameterList createTypeParameterList(PsiTypeParameterListStub stub) {
    return new PsiTypeParameterListImpl(stub);
  }

  @Override
  public PsiAnnotationParameterList createAnnotationParameterList(PsiAnnotationParameterListStub stub) {
    return new PsiAnnotationParamListImpl(stub);
  }

  @Override
  public PsiNameValuePair createNameValuePair(PsiNameValuePairStub stub) {
    return new PsiNameValuePairImpl(stub);
  }

  @Override
  public PsiJavaModule createModule(PsiJavaModuleStub stub) {
    return new PsiJavaModuleImpl(stub);
  }

  @Override
  public PsiRequiresStatement createRequiresStatement(PsiRequiresStatementStub stub) {
    return new PsiRequiresStatementImpl(stub);
  }

  @Override
  public PsiPackageAccessibilityStatement createPackageAccessibilityStatement(PsiPackageAccessibilityStatementStub stub) {
    return new PsiPackageAccessibilityStatementImpl(stub);
  }

  @Override
  public PsiUsesStatement createUsesStatement(PsiUsesStatementStub stub) {
    return new PsiUsesStatementImpl(stub);
  }

  @Override
  public PsiProvidesStatement createProvidesStatement(PsiProvidesStatementStub stub) {
    return new PsiProvidesStatementImpl(stub);
  }

  @Override
  public PsiRecordComponent createRecordComponent(PsiRecordComponentStub stub) {
    return new PsiRecordComponentImpl(stub);
  }

  @Override
  public PsiRecordHeader createRecordHeader(PsiRecordHeaderStub stub) {
    return new PsiRecordHeaderImpl(stub);
  }
}