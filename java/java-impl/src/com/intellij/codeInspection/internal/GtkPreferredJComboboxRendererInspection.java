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

import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.QueryExecutor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class GtkPreferredJComboboxRendererInspection extends BaseJavaLocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return InternalInspectionToolsProvider.GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Preferred JCombobox renderer";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "GtkPreferredJComboboxRenderer";
  }

  public boolean isEnabledByDefault() {
    return true;
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
        if (superClass != null && "javax.swing.DefaultListCellRenderer".equals(superClass.getQualifiedName())){
          holder.registerProblem(superClass, "Please use ListCellRendererWrapper instead to prevent artifacts under GTK+ Look and Feel.");
        }
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if ("javax.swing.DefaultListCellRenderer".equals(aClass.getQualifiedName())){
          holder.registerProblem(aClass.getBaseClassReference(), "Please use ListCellRendererWrapper instead to prevent artifacts under GTK+ Look and Feel.");
        }
      }
    };
  }
}
