/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JavaRequiresAutoModuleInspection extends BaseJavaLocalInspectionTool {
  public boolean TRANSITIVE_ONLY = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.requires.auto.module.option"), this, "TRANSITIVE_ONLY");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return !PsiUtil.isModuleFile(holder.getFile()) ? PsiElementVisitor.EMPTY_VISITOR : new JavaElementVisitor() {
      @Override
      public void visitRequiresStatement(PsiRequiresStatement statement) {
        super.visitRequiresStatement(statement);
        PsiJavaModuleReferenceElement refElement = statement.getReferenceElement();
        if (refElement != null) {
          PsiPolyVariantReference reference = refElement.getReference();
          if (reference != null) {
            PsiElement target = reference.resolve();
            if (target instanceof LightJavaModule) {
              if (!TRANSITIVE_ONLY) {
                holder.registerProblem(refElement, InspectionsBundle.message("inspection.requires.auto.module.message"));
              }
              else if (statement.hasModifierProperty(PsiModifier.TRANSITIVE)) {
                holder.registerProblem(refElement, InspectionsBundle.message("inspection.requires.auto.module.transitive"));
              }
            }
          }
        }
      }
    };
  }
}