// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ReplaceTypeInCastFix implements LocalQuickFix {
  private final String myExistingTypeText;
  private final String myWantedTypeText;
  private final String myWantedTypeCanonicalText;

  public ReplaceTypeInCastFix(PsiType existingType, PsiType wantedType) {
    myExistingTypeText = existingType.getPresentableText();
    myWantedTypeText = wantedType.getPresentableText();
    myWantedTypeCanonicalText = wantedType.getCanonicalText();
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("cast.conflicts.with.instanceof.quickfix1", myExistingTypeText, myWantedTypeText);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.replace.cast.type");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiTypeElement typeElement = ObjectUtils.tryCast(descriptor.getStartElement(), PsiTypeElement.class);
    if (typeElement == null) return;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiTypeElement replacement = factory.createTypeElement(factory.createTypeFromText(myWantedTypeCanonicalText, typeElement));
    typeElement.replace(replacement);
  }
}
