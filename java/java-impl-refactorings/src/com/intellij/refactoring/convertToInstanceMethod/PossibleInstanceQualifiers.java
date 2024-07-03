// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A container for possible qualifiers for 'Convert to instance method' refactoring 
 */
@ApiStatus.Internal
public sealed interface PossibleInstanceQualifiers {
  /**
   * Represents valid list of qualifiers
   * 
   * @param qualifiers list of qualifier parameters
   * @param thisOrNewQualifierName name of 'this/new Object' qualifier, or null if such a name is impossible to use in this context
   */
  record Valid(@NotNull List<@NotNull PsiParameter> qualifiers, @Nullable String thisOrNewQualifierName) implements PossibleInstanceQualifiers {
    Object[] toArray() {
      if (thisOrNewQualifierName == null) {
        return ArrayUtil.toObjectArray(qualifiers);
      }
      return StreamEx.<Object>of(qualifiers).append(thisOrNewQualifierName).toArray();
    }
  }

  /**
   * Represents invalid context in case if conversion to an instance method is not supported
   * 
   * @param message message explaining why conversion is not possible
   */
  record Invalid(@NlsContexts.DialogMessage String message) implements PossibleInstanceQualifiers {
  }

  /**
   * @param method method to convert
   * @return PossibleInstanceQualifiers instance
   */
  static @NotNull PossibleInstanceQualifiers build(@NotNull PsiMethod method) {
    if (!method.hasModifierProperty(PsiModifier.STATIC)) {
      return new Invalid(JavaRefactoringBundle.message("convertToInstanceMethod.method.is.not.static", method.getName()));
    }
    List<PsiParameter> targetQualifiers = new ArrayList<>();
    String thisOrNewQualifierName = null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    boolean classTypesFound = false;
    boolean resolvableClassesFound = false;
    for (final PsiParameter parameter : parameters) {
      if (VariableAccessUtils.variableIsAssigned(parameter, parameter.getDeclarationScope())) continue;
      final PsiType type = parameter.getType();
      if (type instanceof PsiClassType) {
        classTypesFound = true;
        final PsiClass psiClass = ((PsiClassType)type).resolve();
        if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
          resolvableClassesFound = true;
          if (method.getManager().isInProject(psiClass)) {
            targetQualifiers.add(parameter);
          }
        }
      }
    }
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || containingClass.getQualifiedName() == null) {
      return new Invalid(JavaRefactoringBundle.message("convertToInstanceMethod.unsupported.containing.class"));
    }
    String className = containingClass.getName();
    if (!containingClass.isEnum() && !(containingClass instanceof PsiImplicitClass)) {
      PsiMethod[] constructors = containingClass.getConstructors();
      boolean noArgConstructor =
        constructors.length == 0 || ContainerUtil.exists(constructors, constructor -> constructor.getParameterList().isEmpty());
      if (noArgConstructor) {
        thisOrNewQualifierName = "this / new " + className + "()";
      }
    }

    if (!targetQualifiers.isEmpty() || thisOrNewQualifierName != null) {
      return new Valid(targetQualifiers, thisOrNewQualifierName);
    }
    String message;
    if (!classTypesFound) {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.no.parameters.with.reference.type");
    }
    else if (!resolvableClassesFound) {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.have.unknown.types");
    }
    else {
      message = JavaRefactoringBundle.message("convertToInstanceMethod.all.reference.type.parameters.are.not.in.project");
    }
    if (!(containingClass instanceof PsiImplicitClass)) {
      message += " " + JavaRefactoringBundle.message("convertToInstanceMethod.no.default.ctor");
    }
    return new Invalid(message);
  }
}
