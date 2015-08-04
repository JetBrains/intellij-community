/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.impl.source.tree.java.*;

public class SourceStubPsiFactory extends StubPsiFactory {
  @Override
  public PsiClass createClass(PsiClassStub stub) {
    if (stub.isEnumConstantInitializer()) {
      return new PsiEnumConstantInitializerImpl(stub);
    }
    if (stub.isAnonymous()) {
      return new PsiAnonymousClassImpl(stub);
    }
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
    return new PsiReferenceListImpl(stub, stub.getStubType());
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
}
