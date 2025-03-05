// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.naming;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.Set;

public class ConstructorParameterOnFieldRenameRenamer extends AutomaticRenamer {
  @Override
  protected @NonNls String canonicalNameToName(final @NonNls String canonicalName, final PsiNamedElement element) {
    return JavaCodeStyleManager.getInstance(element.getProject()).propertyNameToVariableName(canonicalName, VariableKind.PARAMETER);
  }

  @Override
  protected String nameToCanonicalName(final @NonNls String name, final PsiNamedElement element) {
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(element.getProject());
    final VariableKind variableKind = element instanceof PsiVariable ? javaCodeStyleManager.getVariableKind((PsiVariable)element) : VariableKind.FIELD;
    return javaCodeStyleManager.variableNameToPropertyName(name, variableKind);
  }

  public ConstructorParameterOnFieldRenameRenamer(PsiField aField, String newFieldName) {
    final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(aField.getProject());
    final String propertyName = styleManager.variableNameToPropertyName(aField.getName(), VariableKind.FIELD);
    if (!Comparing.strEqual(propertyName, styleManager.variableNameToPropertyName(newFieldName, VariableKind.FIELD))) {
      final String paramName = styleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      final PsiClass aClass = aField.getContainingClass();
      if (aClass == null) return;
      Set<PsiParameter> toRename = new HashSet<>();
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (constructor instanceof PsiMirrorElement) {
          final PsiElement prototype = ((PsiMirrorElement)constructor).getPrototype();
          if (prototype instanceof PsiMethod && ((PsiMethod)prototype).isConstructor()) {
            constructor = (PsiMethod)prototype;
          }
          else {
            continue;
          }
        }
        if (constructor instanceof LightElement) continue;
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        for (final PsiParameter parameter : parameters) {
          final String parameterName = parameter.getName();
          if (paramName.equals(parameterName) ||
            propertyName.equals(styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER))) {
            toRename.add(parameter);
          }
        }
      }
      myElements.addAll(toRename);

      suggestAllNames(aField.getName(), newFieldName);
    }
  }

  @Override
  public String getDialogTitle() {
    return JavaRefactoringBundle.message("rename.constructor.parameters.title");
  }

  @Override
  public String getDialogDescription() {
    return JavaRefactoringBundle.message("rename.constructor.parameters.with.the.following.names.to");
  }

  @Override
  public String entityName() {
    return JavaRefactoringBundle.message("entity.name.constructor.parameter");
  }
}