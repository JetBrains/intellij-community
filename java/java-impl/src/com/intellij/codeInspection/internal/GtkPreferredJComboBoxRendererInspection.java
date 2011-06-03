/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.internal;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class GtkPreferredJComboBoxRendererInspection extends InternalInspection {
  private static final String RENDERER_CLASS_NAME = DefaultListCellRenderer.class.getName();
  private static final String MESSAGE = "Please use ListCellRendererWrapper instead to prevent artifacts under GTK+ Look and Feel.";

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Preferred JComboBox renderer";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "GtkPreferredJComboBoxRenderer";
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      @Override
      public void visitClass(final PsiClass aClass) {
        final PsiClass superClass = aClass.getSuperClass();
        if (superClass != null && RENDERER_CLASS_NAME.equals(superClass.getQualifiedName())){
          final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();
          holder.registerProblem(nameIdentifier != null ? nameIdentifier : aClass, MESSAGE);
        }
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if (RENDERER_CLASS_NAME.equals(aClass.getBaseClassReference().getQualifiedName())){
          holder.registerProblem(aClass.getBaseClassReference(), MESSAGE);
        }
      }
    };
  }
}
