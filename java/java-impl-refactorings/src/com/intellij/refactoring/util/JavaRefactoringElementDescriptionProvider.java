// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.findUsages.DescriptiveNameUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

public final class JavaRefactoringElementDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(@NotNull final PsiElement element, @NotNull final ElementDescriptionLocation location) {
    if (!(location instanceof RefactoringDescriptionLocation rdLocation)) return null;

    if (element instanceof PsiField field) {
      int options = PsiFormatUtilBase.SHOW_NAME;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      }
      String formattedVariable = PsiFormatUtil.formatVariable(field, options, PsiSubstitutor.EMPTY);
      return JavaRefactoringBundle.message(element instanceof PsiEnumConstant ? "enum.constant.description" : "field.description",
                                           CommonRefactoringUtil.htmlEmphasize(formattedVariable));
    }

    if (element instanceof PsiMethod method) {
      int options = PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS;
      if (rdLocation.includeParent()) {
        options |= PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
      }
      String descriptiveName = CommonRefactoringUtil.htmlEmphasize(PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, options, PsiFormatUtilBase.SHOW_TYPE));
      return method.isConstructor() ?
             JavaRefactoringBundle.message("constructor.description", descriptiveName) :
             JavaRefactoringBundle.message("method.description", descriptiveName);
    }

    if (element instanceof PsiClassInitializer initializer) {
      boolean isStatic = initializer.hasModifierProperty(PsiModifier.STATIC);
      if (!rdLocation.includeParent()) {
        return JavaRefactoringBundle.message("class.initializer.description", isStatic ? 0 : 1);
      }
      String description = getElementDescription(initializer.getContainingClass(), RefactoringDescriptionLocation.WITHOUT_PARENT);
      return isStatic
             ? JavaRefactoringBundle.message("static.initializer.description", description)
             : JavaRefactoringBundle.message("instance.initializer.description", description);
    }

    if (element instanceof PsiParameter parameter) {
      if (parameter.getDeclarationScope() instanceof PsiForeachStatement) {
        return JavaRefactoringBundle.message("local.variable.description", CommonRefactoringUtil.htmlEmphasize(parameter.getName()));
      }
      return JavaRefactoringBundle.message("parameter.description", CommonRefactoringUtil.htmlEmphasize(parameter.getName()));
    }

    if (element instanceof PsiLocalVariable variable) {
      return JavaRefactoringBundle.message("local.variable.description", CommonRefactoringUtil.htmlEmphasize(variable.getName()));
    }

    if (element instanceof PsiPackage aPackage) {
      return JavaRefactoringBundle.message("package.description", CommonRefactoringUtil.htmlEmphasize(aPackage.getName()));
    }

    if ((element instanceof PsiClass aClass)) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        String description = getElementDescription(aClass.getParent(), location);
        return JavaRefactoringBundle.message("class.body.description", description);
      }
      else if (aClass instanceof PsiAnonymousClass anonymousClass) {
        String descriptiveName = CommonRefactoringUtil.htmlEmphasize(anonymousClass.getBaseClassReference().getText());
        return JavaRefactoringBundle.message("anonymous.class.description", descriptiveName);
      }
      String descriptiveName = CommonRefactoringUtil.htmlEmphasize(DescriptiveNameUtil.getDescriptiveName(aClass));
      int local = aClass.getParent() instanceof PsiDeclarationStatement ? 1 : 0;
      if (aClass.isInterface()) {
        return JavaRefactoringBundle.message("interface.description", descriptiveName, local);
      }
      else if (aClass.isEnum()) {
        return JavaRefactoringBundle.message("enum.description", descriptiveName, local);
      }
      else if (aClass.isRecord()) {
        return JavaRefactoringBundle.message("record.description", descriptiveName, local);
      }
      else {
        return JavaRefactoringBundle.message("class.description", descriptiveName, local);
      }
    }
    return null;
  }
}
