/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AddSerialVersionUIDFix extends PsiUpdateModCommandQuickFix {

  @Override
  public @NotNull String getFamilyName() {
    return InspectionGadgetsBundle.message("add.serialversionuidfield.quickfix");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement classIdentifier, @NotNull ModPsiUpdater updater) {
    final PsiClass aClass = (PsiClass)classIdentifier.getParent();
    assert aClass != null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(aClass.getProject());
    final long serialVersionUID = SerialVersionUIDBuilder.computeDefaultSUID(aClass);
    final PsiField field = elementFactory.createFieldFromText(generateSerialVersionUIDFieldText(serialVersionUID), aClass);
    if (PsiUtil.isAvailable(JavaFeature.SERIAL_ANNOTATION, field)) {
      annotateFieldWithSerial(field);
    }
    aClass.add(field);
  }

  public static @NonNls String generateSerialVersionUIDFieldText(long serialVersionUID) {
    return "private static final long serialVersionUID = " + serialVersionUID + "L;";
  }

  public static void annotateFieldWithSerial(@NotNull PsiField field) {
    PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return;
    PsiAnnotation annotation = AddAnnotationPsiFix
      .addPhysicalAnnotationIfAbsent(CommonClassNames.JAVA_IO_SERIAL, PsiNameValuePair.EMPTY_ARRAY, modifierList);
    if (annotation != null) {
      JavaCodeStyleManager.getInstance(field.getProject()).shortenClassReferences(annotation);
    }
  }
}
