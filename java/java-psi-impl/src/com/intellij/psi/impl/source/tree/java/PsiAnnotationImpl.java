/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.PsiAnnotationStub;
import com.intellij.psi.impl.meta.MetaRegistry;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class PsiAnnotationImpl extends JavaStubPsiElement<PsiAnnotationStub> implements PsiAnnotation {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl");
  
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR = new PairFunction<Project, String, PsiAnnotation>() {
    @Override
    public PsiAnnotation fun(Project project, String text) {
      return JavaPsiFacade.getInstance(project).getElementFactory().createAnnotationFromText(text, null);
    }
  };

  public PsiAnnotationImpl(final PsiAnnotationStub stub) {
    super(stub, JavaStubElementTypes.ANNOTATION);
  }

  public PsiAnnotationImpl(final ASTNode node) {
    super(node);
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    final PsiAnnotationStub stub = getStub();
    if (stub != null) {
      return (PsiJavaCodeReferenceElement)stub.getTreeElement().findChildByRoleAsPsiElement(ChildRole.CLASS_REFERENCE);
    }

    final Object result = PsiTreeUtil.getChildOfType(this, PsiJavaCodeReferenceElement.class);
    if (result != null && !(result instanceof PsiJavaCodeReferenceElement)) {
      throw new AssertionError("getChildOfType returned rubbish: " + result);
    }
    return (PsiJavaCodeReferenceElement)result;
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  public String toString() {
    return "PsiAnnotation";
  }

  @Override
  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return getRequiredStubOrPsiChild(JavaStubElementTypes.ANNOTATION_PARAMETER_LIST);
  }

  @Override
  @Nullable public String getQualifiedName() {
    final PsiJavaCodeReferenceElement nameRef = getNameReferenceElement();
    if (nameRef == null) return null;
    return nameRef.getCanonicalText();
  }

  @Override
  public final void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitAnnotation(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiMetaData getMetaData() {
    return MetaRegistry.getMetaBase(this);
  }

  @Nullable
  @Override
  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    if (parent instanceof PsiTypeElement) {
      return ((PsiTypeElement)parent).getOwner(this);
    }
    if (parent instanceof PsiTypeParameter) {
      return (PsiAnnotationOwner)parent;
    }
    PsiElement member = parent.getParent();
    String[] elementTypeFields = getApplicableElementTypeFields(member);
    if (elementTypeFields == null) return null;
    if (parent instanceof PsiAnnotationOwner
        && isAnnotationApplicableTo(this, true, elementTypeFields)) return (PsiAnnotationOwner)parent;

    PsiAnnotationOwner typeElement;
    if (member instanceof PsiVariable) {
      typeElement = ((PsiVariable)member).getTypeElement();
    }
    else if (member instanceof PsiMethod && !((PsiMethod)member).isConstructor()) {
      typeElement = ((PsiMethod)member).getReturnTypeElement();
    }
    else if (parent instanceof PsiAnnotationOwner) {
      typeElement = (PsiAnnotationOwner)parent;
    }
    else {
      typeElement = null;
    }
    return typeElement;
  }

  public static boolean isAnnotationApplicableTo(PsiAnnotation annotation, boolean strict, String... elementTypeFields) {
    if (elementTypeFields == null) return true;
    PsiJavaCodeReferenceElement nameRef = annotation.getNameReferenceElement();
    if (nameRef == null) {
      return !strict;
    }
    PsiElement resolved = nameRef.resolve();
    if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
      return !strict;
    }
    PsiClass annotationType = (PsiClass)resolved;
    return isAnnotationApplicable(strict, annotationType, elementTypeFields, annotation.getResolveScope());
  }

  public static boolean isAnnotationApplicable(boolean strict,
                                               @NotNull PsiClass annotationType,
                                               @Nullable String[] elementTypeFields,
                                               GlobalSearchScope resolveScope) {
    if (elementTypeFields == null) {
      return !strict;
    }

    PsiAnnotation target = annotationType.getModifierList().findAnnotation(CommonClassNames.TARGET_ANNOTATION_FQ_NAME);
    if (target == null) {
      //todo hack: ambiguity in spec
      return !strict;
      //return !ArrayUtil.contains("TYPE_USE", elementTypeFields);
    }
    PsiNameValuePair[] attributes = target.getParameterList().getAttributes();
    if (attributes.length == 0) {
      return !strict;
    }
    LOG.assertTrue(elementTypeFields.length > 0);

    PsiClass elementTypeClass =
      JavaPsiFacade.getInstance(annotationType.getProject()).findClass("java.lang.annotation.ElementType", resolveScope);
    if (elementTypeClass == null) {
      //todo hack
      return !strict;
      //return !ArrayUtil.contains("TYPE_USE", elementTypeFields);
    }

    PsiAnnotationMemberValue value = null;
    for (String fieldName : elementTypeFields) {
      PsiField field = elementTypeClass.findFieldByName(fieldName, false);
      if (field == null) {
        continue;
      }
      if (value == null) {
        value = attributes[0].getValue();
      }
      if (value instanceof PsiArrayInitializerMemberValue) {
        PsiAnnotationMemberValue[] initializers = ((PsiArrayInitializerMemberValue)value).getInitializers();
        for (PsiAnnotationMemberValue initializer : initializers) {
          if (initializer instanceof PsiReference) {
            if (((PsiReference)initializer).isReferenceTo(field)) {
              return true;
            }
          }
        }
      }
      else if (value instanceof PsiReference) {
        if (((PsiReference)value).isReferenceTo(field)) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  public static String[] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)owner;
      if (aClass.isAnnotationType()) {
        return new String[]{"ANNOTATION_TYPE", "TYPE"};
      }
      else if (aClass instanceof PsiTypeParameter) {
        return new String[]{"TYPE_PARAMETER"};
      }
      else {
        return new String[]{"TYPE"};
      }
    }
    if (owner instanceof PsiMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return new String[]{"CONSTRUCTOR"};
      }
      else {
        return new String[]{"METHOD"};
      }
    }
    if (owner instanceof PsiField) {
      return new String[]{"FIELD"};
    }
    if (owner instanceof PsiParameter) {
      return new String[]{"PARAMETER"};
    }
    if (owner instanceof PsiLocalVariable) {
      return new String[]{"LOCAL_VARIABLE"};
    }
    if (owner instanceof PsiPackageStatement) {
      return new String[]{"PACKAGE"};
    }
    if (owner instanceof PsiTypeElement) {
      return new String[]{"TYPE_USE"};
    }

    return null;
  }
}