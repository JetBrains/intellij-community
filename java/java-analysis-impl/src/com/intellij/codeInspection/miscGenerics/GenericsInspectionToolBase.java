/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public abstract class GenericsInspectionToolBase extends BaseJavaBatchLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (!PsiUtil.isLanguageLevel5OrHigher(file)) return new PsiElementVisitor() {
    };

    return super.buildVisitor(holder, isOnTheFly);
  }

  @Override
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiClassInitializer[] initializers = aClass.getInitializers();
    if (initializers.length == 0) return null;
    List<ProblemDescriptor> descriptors = new ArrayList<>();
    for (PsiClassInitializer initializer : initializers) {
      final ProblemDescriptor[] localDescriptions = getDescriptions(initializer, manager, isOnTheFly);
      if (localDescriptions != null) {
        ContainerUtil.addAll(descriptors, localDescriptions);
      }
    }
    if (descriptors.isEmpty()) return null;
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  @Override
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiExpression initializer = field.getInitializer();
    if (initializer != null) {
      return getDescriptions(initializer, manager, isOnTheFly);
    }
    if (field instanceof PsiEnumConstant) {
      return getDescriptions(field, manager, isOnTheFly);
    }
    return null;
  }

  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod psiMethod, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiCodeBlock body = psiMethod.getBody();
    if (body != null) {
      return getDescriptions(body, manager, isOnTheFly);
    }
    return null;
  }

  @Nullable
  public abstract ProblemDescriptor[] getDescriptions(@NotNull PsiElement place, @NotNull InspectionManager manager, boolean isOnTheFly);
}
