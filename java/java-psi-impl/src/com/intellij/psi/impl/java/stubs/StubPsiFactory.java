/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

public abstract class StubPsiFactory {
  public abstract PsiClass createClass(PsiClassStub stub);

  public abstract PsiAnnotation createAnnotation(PsiAnnotationStub stub);

  public abstract PsiClassInitializer createClassInitializer(PsiClassInitializerStub stub);

  public abstract PsiReferenceList createClassReferenceList(PsiClassReferenceListStub stub);

  public abstract PsiField createField(PsiFieldStub stub);

  public abstract PsiImportList createImportList(PsiImportListStub stub);

  public abstract PsiImportStatementBase createImportStatement(PsiImportStatementStub stub);

  public abstract PsiMethod createMethod(PsiMethodStub stub);

  public abstract PsiModifierList createModifierList(PsiModifierListStub stub);

  public abstract PsiParameter createParameter(PsiParameterStub stub);

  public abstract PsiParameterList createParameterList(PsiParameterListStub stub);

  public abstract PsiTypeParameter createTypeParameter(PsiTypeParameterStub stub);

  public abstract PsiTypeParameterList createTypeParameterList(PsiTypeParameterListStub stub);

  public abstract PsiAnnotationParameterList createAnnotationParameterList(PsiAnnotationParameterListStub stub);

  public abstract PsiNameValuePair createNameValuePair(PsiNameValuePairStub stub);
}
