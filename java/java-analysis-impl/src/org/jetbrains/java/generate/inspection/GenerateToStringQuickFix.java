/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.GenerateToStringActionHandler;

/**
 * Quick fix to run Generate toString() to fix any code inspection problems.
 */
public final class GenerateToStringQuickFix implements LocalQuickFix {

  public static final GenerateToStringQuickFix INSTANCE = new GenerateToStringQuickFix();

  private GenerateToStringQuickFix() {}

  public static GenerateToStringQuickFix getInstance() {
    return INSTANCE;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("generate.to.string.quick.fix.text");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("generate.to.string.quick.fix.family.name");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor desc) {
    final PsiClass clazz = PsiTreeUtil.getParentOfType(desc.getPsiElement(), PsiClass.class);
    if (clazz == null) {
      return; // no class to fix
    }
    GenerateToStringActionHandler handler = ApplicationManager.getApplication().getService(GenerateToStringActionHandler.class);
    handler.executeActionQuickFix(project, clazz);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean availableInBatchMode() {
    return false;
  }
}
