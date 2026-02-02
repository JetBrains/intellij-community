// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiProvidesStatement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiRequiresStatement;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiUsesStatement;
import com.intellij.psi.impl.source.PsiAnnotationMethodImpl;
import com.intellij.psi.impl.source.PsiAnonymousClassImpl;
import com.intellij.psi.impl.source.PsiClassImpl;
import com.intellij.psi.impl.source.PsiClassInitializerImpl;
import com.intellij.psi.impl.source.PsiEnumConstantImpl;
import com.intellij.psi.impl.source.PsiEnumConstantInitializerImpl;
import com.intellij.psi.impl.source.PsiFieldImpl;
import com.intellij.psi.impl.source.PsiImplicitClassImpl;
import com.intellij.psi.impl.source.PsiImportListImpl;
import com.intellij.psi.impl.source.PsiImportModuleStatementImpl;
import com.intellij.psi.impl.source.PsiImportStatementImpl;
import com.intellij.psi.impl.source.PsiImportStaticStatementImpl;
import com.intellij.psi.impl.source.PsiJavaModuleImpl;
import com.intellij.psi.impl.source.PsiMethodImpl;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.impl.source.PsiPackageAccessibilityStatementImpl;
import com.intellij.psi.impl.source.PsiParameterImpl;
import com.intellij.psi.impl.source.PsiParameterListImpl;
import com.intellij.psi.impl.source.PsiProvidesStatementImpl;
import com.intellij.psi.impl.source.PsiRecordComponentImpl;
import com.intellij.psi.impl.source.PsiRecordHeaderImpl;
import com.intellij.psi.impl.source.PsiReferenceListImpl;
import com.intellij.psi.impl.source.PsiRequiresStatementImpl;
import com.intellij.psi.impl.source.PsiUsesStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationParamListImpl;
import com.intellij.psi.impl.source.tree.java.PsiNameValuePairImpl;
import com.intellij.psi.impl.source.tree.java.PsiPackageStatementImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterImpl;
import com.intellij.psi.impl.source.tree.java.PsiTypeParameterListImpl;

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