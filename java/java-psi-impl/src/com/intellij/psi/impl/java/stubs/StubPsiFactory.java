// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.*;

public abstract class StubPsiFactory {
  public abstract PsiClass createClass(PsiClassStub stub);

  public abstract PsiAnnotation createAnnotation(PsiAnnotationStub stub);

  public abstract PsiClassInitializer createClassInitializer(PsiClassInitializerStub stub);

  public abstract PsiReferenceList createClassReferenceList(PsiClassReferenceListStub stub);

  public abstract PsiField createField(PsiFieldStub stub);

  public abstract PsiImportList createImportList(PsiImportListStub stub);

  public abstract PsiImportStatementBase createImportStatement(PsiImportStatementStub stub);

  public abstract PsiMethod createMethod(PsiMethodStub stub);
  
  public PsiPackageStatement createPackageStatement(PsiPackageStatementStub stub) {
    return null;
  }

  public abstract PsiModifierList createModifierList(PsiModifierListStub stub);

  public abstract PsiParameter createParameter(PsiParameterStub stub);

  public abstract PsiParameterList createParameterList(PsiParameterListStub stub);

  public abstract PsiTypeParameter createTypeParameter(PsiTypeParameterStub stub);

  public abstract PsiTypeParameterList createTypeParameterList(PsiTypeParameterListStub stub);

  public abstract PsiAnnotationParameterList createAnnotationParameterList(PsiAnnotationParameterListStub stub);

  public abstract PsiNameValuePair createNameValuePair(PsiNameValuePairStub stub);

  public PsiJavaModule createModule(PsiJavaModuleStub stub) {
    return null;
  }

  public PsiRequiresStatement createRequiresStatement(PsiRequiresStatementStub stub) {
    return null;
  }

  public PsiPackageAccessibilityStatement createPackageAccessibilityStatement(PsiPackageAccessibilityStatementStub stub) {
    return null;
  }

  public PsiUsesStatement createUsesStatement(PsiUsesStatementStub stub) {
    return null;
  }

  public PsiProvidesStatement createProvidesStatement(PsiProvidesStatementStub stub) {
    return null;
  }

  public PsiRecordComponent createRecordComponent(PsiRecordComponentStub stub) {
    return null;
  }

  public PsiRecordHeader createRecordHeader(PsiRecordHeaderStub stub) {
    return null;
  }
}