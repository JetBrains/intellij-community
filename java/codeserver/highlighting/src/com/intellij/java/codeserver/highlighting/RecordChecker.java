// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.highlighting;

import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.java.codeserver.highlighting.errors.JavaIncompatibleTypeErrorContext;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiSuperMethodImplUtil;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.AccessModifier;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

final class RecordChecker {
  private final @NotNull JavaErrorVisitor myVisitor;

  RecordChecker(@NotNull JavaErrorVisitor visitor) { myVisitor = visitor; }

  void checkRecordHeader(@NotNull PsiClass psiClass) {
    PsiRecordHeader header = psiClass.getRecordHeader();
    if (!psiClass.isRecord()) {
      if (header != null) {
        myVisitor.report(JavaErrorKinds.RECORD_HEADER_REGULAR_CLASS.create(header));
      }
      return;
    }
    PsiIdentifier identifier = psiClass.getNameIdentifier();
    if (identifier == null) return;
    if (header == null) {
      myVisitor.report(JavaErrorKinds.RECORD_NO_HEADER.create(psiClass));
    }
  }

  void checkRecordComponentWellFormed(@NotNull PsiRecordComponent component) {
    if (component.isVarArgs() && PsiTreeUtil.getNextSiblingOfType(component, PsiRecordComponent.class) != null) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_VARARG_NOT_LAST.create(component));
      return;
    }
    TextRange range = MethodChecker.getCStyleDeclarationRange(component);
    if (range != null) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_CSTYLE_DECLARATION.create(component, range));
      return;
    }
    PsiIdentifier identifier = component.getNameIdentifier();
    if (identifier != null && JavaPsiRecordUtil.ILLEGAL_RECORD_COMPONENT_NAMES.contains(identifier.getText())) {
      myVisitor.report(JavaErrorKinds.RECORD_COMPONENT_RESTRICTED_NAME.create(component));
    }
  }

  void checkRecordAccessorReturnType(@NotNull PsiRecordComponent component) {
    String componentName = component.getName();
    PsiTypeElement typeElement = component.getTypeElement();
    if (typeElement == null) return;
    PsiClass containingClass = component.getContainingClass();
    if (containingClass == null) return;
    PsiMethod[] methods = containingClass.findMethodsByName(componentName, false);
    for (PsiMethod method : methods) {
      if (method instanceof LightRecordMethod) {
        List<HierarchicalMethodSignature> superSignatures =
          PsiSuperMethodImplUtil.getHierarchicalMethodSignature(method, method.getResolveScope()).getSuperSignatures();
        MethodSignatureBackedByPsiMethod signature = MethodSignatureBackedByPsiMethod.create(method, PsiSubstitutor.EMPTY);
        myVisitor.myMethodChecker.checkMethodIncompatibleReturnType(component, signature, superSignatures);
      }
    }
  }

  void checkRecordAccessorDeclaration(@NotNull PsiMethod method) {
    PsiRecordComponent component = JavaPsiRecordUtil.getRecordComponentForAccessor(method);
    if (component == null) return;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    PsiType componentType = component.getType();
    PsiType methodType = method.getReturnType();
    if (methodType == null) return; // Either constructor or incorrect method, will be reported in another way
    if (componentType instanceof PsiEllipsisType ellipsisType) {
      componentType = ellipsisType.getComponentType().createArrayType();
    }
    if (!componentType.equals(methodType)) {
      myVisitor.report(JavaErrorKinds.RECORD_ACCESSOR_WRONG_RETURN_TYPE.create(
        method, new JavaIncompatibleTypeErrorContext(componentType, methodType)));
      return;
    }
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
      myVisitor.report(JavaErrorKinds.RECORD_ACCESSOR_NON_PUBLIC.create(method));
      return;
    }
    checkRecordSpecialMethodDeclaration(method);
  }

  private void checkRecordSpecialMethodDeclaration(@NotNull PsiMethod method) {
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    if (typeParameterList != null && typeParameterList.getTypeParameters().length > 0) {
      myVisitor.report(JavaErrorKinds.RECORD_SPECIAL_METHOD_TYPE_PARAMETERS.create(typeParameterList));
      return;
    }
    if (method.isConstructor()) {
      AccessModifier modifier = AccessModifier.fromModifierList(method.getModifierList());
      PsiModifierList classModifierList = Objects.requireNonNull(method.getContainingClass()).getModifierList();
      if (classModifierList != null) {
        AccessModifier classModifier = AccessModifier.fromModifierList(classModifierList);
        if (classModifier.isWeaker(modifier)) {
          myVisitor.report(JavaErrorKinds.RECORD_CONSTRUCTOR_STRONGER_ACCESS.create(method, classModifier));
          return;
        }
      }
    }
    PsiReferenceList throwsList = method.getThrowsList();
    if (throwsList.getReferenceElements().length > 0) {
      myVisitor.report(JavaErrorKinds.RECORD_SPECIAL_METHOD_THROWS.create(throwsList));
    }
  }

  void checkRecordConstructorDeclaration(@NotNull PsiMethod method) {
    if (!method.isConstructor()) return;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;
    PsiIdentifier identifier = method.getNameIdentifier();
    if (identifier == null) return;
    if (!aClass.isRecord()) {
      if (JavaPsiRecordUtil.isCompactConstructor(method)) {
        myVisitor.report(JavaErrorKinds.METHOD_NO_PARAMETER_LIST.create(method));
      }
      return;
    }
    if (JavaPsiRecordUtil.isExplicitCanonicalConstructor(method)) {
      PsiParameter[] parameters = method.getParameterList().getParameters();
      PsiRecordComponent[] components = aClass.getRecordComponents();
      assert parameters.length == components.length;
      for (int i = 0; i < parameters.length; i++) {
        PsiRecordComponent component = components[i];
        PsiParameter parameter = parameters[i];
        if (!parameter.getType().equals(component.getType())) {
          myVisitor.report(JavaErrorKinds.RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_TYPE.create(parameter, component));
        }
        else if (!parameter.getName().equals(component.getName())) {
          myVisitor.report(JavaErrorKinds.RECORD_CANONICAL_CONSTRUCTOR_WRONG_PARAMETER_NAME.create(parameter, component));
        }
      }
      checkRecordSpecialMethodDeclaration(method);
      return;
    }
    if (JavaPsiRecordUtil.isCompactConstructor(method)) {
      checkRecordSpecialMethodDeclaration(method);
      return;
    }
    // Non-canonical constructor
    PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
    if (call == null || JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
      myVisitor.report(JavaErrorKinds.RECORD_NO_CONSTRUCTOR_CALL_IN_NON_CANONICAL.create(method));
    }
  }
}
