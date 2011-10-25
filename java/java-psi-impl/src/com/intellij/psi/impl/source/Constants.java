/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.TokenSet;

public interface Constants extends ElementType {

  PsiElementArrayConstructor<PsiClass> PSI_CLASS_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClass>() {
    @Override
    public PsiClass[] newPsiElementArray(int length) {
      return length == 0 ? PsiClass.EMPTY_ARRAY : new PsiClass[length];
    }
  };

  PsiElementArrayConstructor<PsiField> PSI_FIELD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiField>() {
    @Override
    public PsiField[] newPsiElementArray(int length) {
      return length == 0 ? PsiField.EMPTY_ARRAY : new PsiField[length];
    }
  };

  PsiElementArrayConstructor<PsiMethod> PSI_METHOD_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiMethod>() {
    @Override
    public PsiMethod[] newPsiElementArray(int length) {
      return length == 0 ? PsiMethod.EMPTY_ARRAY : new PsiMethod[length];
    }
  };

  PsiElementArrayConstructor<PsiClassInitializer> PSI_CLASS_INITIALIZER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiClassInitializer>() {
    @Override
    public PsiClassInitializer[] newPsiElementArray(int length) {
      return length == 0 ? PsiClassInitializer.EMPTY_ARRAY : new PsiClassInitializer[length];
    }
  };

  PsiElementArrayConstructor<PsiParameter> PSI_PARAMETER_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiParameter>() {
    @Override
    public PsiParameter[] newPsiElementArray(int length) {
      return length == 0 ? PsiParameter.EMPTY_ARRAY : new PsiParameter[length];
    }
  };

  PsiElementArrayConstructor<PsiCatchSection> PSI_CATCH_SECTION_ARRAYS_CONSTRUCTOR = new PsiElementArrayConstructor<PsiCatchSection>() {
    @Override
    public PsiCatchSection[] newPsiElementArray(int length) {
      return length == 0 ? PsiCatchSection.EMPTY_ARRAY : new PsiCatchSection[length];
    }
  };

  PsiElementArrayConstructor<PsiJavaCodeReferenceElement> PSI_REFERENCE_ELEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiJavaCodeReferenceElement>() {
    @Override
    public PsiJavaCodeReferenceElement[] newPsiElementArray(int length) {
      return length == 0 ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : new PsiJavaCodeReferenceElement[length];
    }
  };

  PsiElementArrayConstructor<PsiStatement> PSI_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiStatement>() {
    @Override
    public PsiStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiStatement.EMPTY_ARRAY : new PsiStatement[length];
    }
  };

  PsiElementArrayConstructor<PsiExpression> PSI_EXPRESSION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiExpression>() {
    @Override
    public PsiExpression[] newPsiElementArray(int length) {
      return length == 0 ? PsiExpression.EMPTY_ARRAY : new PsiExpression[length];
    }
  };

  PsiElementArrayConstructor<PsiImportStatement> PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatement>() {
    @Override
    public PsiImportStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStatement.EMPTY_ARRAY : new PsiImportStatement[length];
    }
  };

  PsiElementArrayConstructor<PsiImportStaticStatement> PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStaticStatement>() {
    @Override
    public PsiImportStaticStatement[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStaticStatement.EMPTY_ARRAY : new PsiImportStaticStatement[length];
    }
  };


  PsiElementArrayConstructor<PsiImportStatementBase> PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiImportStatementBase>() {
    @Override
    public PsiImportStatementBase[] newPsiElementArray(int length) {
      return length == 0 ? PsiImportStatementBase.EMPTY_ARRAY : new PsiImportStatementBase[length];
    }
  };

  PsiElementArrayConstructor<PsiAnnotationMemberValue> PSI_ANNOTATION_MEMBER_VALUE_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotationMemberValue>() {
    @Override
    public PsiAnnotationMemberValue[] newPsiElementArray(int length) {
      return length == 0 ? PsiAnnotationMemberValue.EMPTY_ARRAY : new PsiAnnotationMemberValue[length];
    }
  };

  PsiElementArrayConstructor<PsiNameValuePair> PSI_NAME_VALUE_PAIR_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiNameValuePair>() {
    @Override
    public PsiNameValuePair[] newPsiElementArray(int length) {
      return length == 0 ? PsiNameValuePair.EMPTY_ARRAY : new PsiNameValuePair[length];
    }
  };

  PsiElementArrayConstructor<PsiAnnotation> PSI_ANNOTATION_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor<PsiAnnotation>() {
    @Override
    public PsiAnnotation[] newPsiElementArray(int length) {
      return length == 0 ? PsiAnnotation.EMPTY_ARRAY : new PsiAnnotation[length];
    }
  };

  TokenSet CLASS_BIT_SET = TokenSet.create(JavaElementType.CLASS, JavaElementType.ANONYMOUS_CLASS, JavaElementType.ENUM_CONSTANT_INITIALIZER);
  TokenSet FIELD_BIT_SET = TokenSet.create(JavaElementType.FIELD, JavaElementType.ENUM_CONSTANT);
  TokenSet METHOD_BIT_SET = TokenSet.create(JavaElementType.METHOD, JavaElementType.ANNOTATION_METHOD);
  TokenSet CLASS_INITIALIZER_BIT_SET = TokenSet.create(JavaElementType.CLASS_INITIALIZER);
  TokenSet PARAMETER_BIT_SET = TokenSet.create(JavaElementType.PARAMETER);
  TokenSet CATCH_SECTION_BIT_SET = TokenSet.create(JavaElementType.CATCH_SECTION);
  TokenSet JAVA_CODE_REFERENCE_BIT_SET = TokenSet.create(JavaElementType.JAVA_CODE_REFERENCE);
  TokenSet NAME_VALUE_PAIR_BIT_SET = TokenSet.create(JavaElementType.NAME_VALUE_PAIR);
  TokenSet ANNOTATION_BIT_SET = TokenSet.create(JavaElementType.ANNOTATION);
}
