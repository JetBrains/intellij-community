/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;

public abstract class InternalInspection extends BaseJavaLocalInspectionTool {
  private static final Key<Boolean> INTERNAL_INSPECTIONS = Key.create("idea.internal.inspections.enabled");
  private static final String MARKER_CLASS = JBList.class.getName();
  private static final PsiElementVisitor EMPTY_VISITOR = new PsiElementVisitor() { };

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return isAllowed(holder.getProject()) ? buildInternalVisitor(holder, isOnTheFly) : EMPTY_VISITOR;
  }

  @NotNull
  @Override
  public final PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                              boolean isOnTheFly,
                                              @NotNull LocalInspectionToolSession session) {
    return isAllowed(holder.getProject()) ? buildInternalVisitor(holder, isOnTheFly) : EMPTY_VISITOR;
  }

  private static boolean isAllowed(@NotNull Project project) {
    Boolean flag = project.getUserData(INTERNAL_INSPECTIONS);
    if (flag == null) {
      final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      flag = JavaPsiFacade.getInstance(project).findClass(MARKER_CLASS, scope) != null;
      project.putUserData(INTERNAL_INSPECTIONS, flag);
    }
    return flag;
  }

  public abstract PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);
}
