// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiClassReferenceTypePointerFactory implements ClassTypePointerFactory {
  private static final Logger LOG = Logger.getInstance(PsiClassReferenceTypePointerFactory.class);

  @Override
  public @Nullable SmartTypePointer createClassTypePointer(@NotNull PsiClassType classType, @NotNull Project project) {
    if (classType instanceof PsiClassReferenceType) {
      return new ClassReferenceTypePointer((PsiClassReferenceType)classType, project);
    }

    return null;
  }

  private static class ClassReferenceTypePointer extends TypePointerBase<PsiClassReferenceType> {
    private final SmartPsiElementPointer<PsiJavaCodeReferenceElement> mySmartPsiElementPointer;
    private final String myReferenceText;
    private final Project myProject;

    ClassReferenceTypePointer(@NotNull PsiClassReferenceType type, Project project) {
      super(type);
      myProject = project;

      final PsiJavaCodeReferenceElement reference = type.getReference();
      mySmartPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(reference);
      myReferenceText = reference.getText();
    }

    @Override
    protected PsiClassReferenceType calcType() {
      PsiClassReferenceType myType = null;
      final PsiJavaCodeReferenceElement referenceElement = mySmartPsiElementPointer.getElement();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(myProject);
      if (referenceElement != null) {
        myType = (PsiClassReferenceType)factory.createType(referenceElement);
      }
      else {
        try {
          myType = (PsiClassReferenceType)factory.createTypeFromText(myReferenceText, null);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      return myType;
    }
  }

}
