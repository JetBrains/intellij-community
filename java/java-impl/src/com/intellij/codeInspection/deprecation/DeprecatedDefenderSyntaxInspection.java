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
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiModifierListImpl;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// todo[r.sh] drop this after transition period finished
public class DeprecatedDefenderSyntaxInspection extends BaseJavaLocalInspectionTool {
  private final LocalQuickFix myQuickFix = new MyQuickFix();

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    final PsiJavaToken marker = PsiModifierListImpl.findExtensionMethodMarker(method);
    return marker == null ? null : new ProblemDescriptor[]{
      manager.createProblemDescriptor(marker, getDisplayName(), myQuickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
    };
  }

  private static class MyQuickFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("deprecated.defender.syntax.fix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement marker = descriptor.getPsiElement();
      if (marker != null && PsiUtil.isJavaToken(marker, JavaTokenType.DEFAULT_KEYWORD)) {
        final PsiElement parent = marker.getParent();
        if (parent instanceof PsiMethod) {
          marker.delete();
          final PsiMethod method = (PsiMethod)parent;
          if (!method.hasModifierProperty(PsiModifier.DEFAULT)) {
            PsiUtil.setModifierProperty(method, PsiModifier.DEFAULT, true);
          }
        }
      }
    }
  }
}
