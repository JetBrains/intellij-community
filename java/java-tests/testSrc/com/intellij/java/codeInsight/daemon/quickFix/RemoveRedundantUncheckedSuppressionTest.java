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
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;


public class RemoveRedundantUncheckedSuppressionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    final PossibleHeapPollutionVarargsInspection varargsInspection = new PossibleHeapPollutionVarargsInspection();
    final UncheckedWarningLocalInspection warningLocalInspection = new UncheckedWarningLocalInspection();
    final RedundantSuppressInspection inspection = new RedundantSuppressInspection(){
      @NotNull
      @Override
      protected InspectionToolWrapper[] getInspectionTools(PsiElement psiElement, @NotNull InspectionManager manager) {
        return new InspectionToolWrapper[]{
          new LocalInspectionToolWrapper(varargsInspection),
          new LocalInspectionToolWrapper(warningLocalInspection)
        };
      }
    };

    return new LocalInspectionTool[] {
      new LocalInspectionTool() {
        @Nls
        @NotNull
        @Override
        public String getGroupDisplayName() {
          return inspection.getGroupDisplayName();
        }

        @Nls
        @NotNull
        @Override
        public String getDisplayName() {
          return inspection.getDisplayName();
        }

        @NotNull
        @Override
        public String getShortName() {
          return inspection.getShortName();
        }

        @NotNull
        @Override
        public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                              boolean isOnTheFly,
                                              @NotNull LocalInspectionToolSession session) {
          return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {
              checkMember(aClass, inspection, holder);
            }

            @Override
            public void visitMethod(PsiMethod method) {
              checkMember(method, inspection, holder);
            }
          };
        }

        private void checkMember(PsiMember member, RedundantSuppressInspection inspection, ProblemsHolder holder) {
          final ProblemDescriptor[] problemDescriptors = inspection.checkElement(member, InspectionManager.getInstance(getProject()));
          if (problemDescriptors != null) {
            for (ProblemDescriptor problemDescriptor : problemDescriptors) {
              holder.registerProblem(problemDescriptor);
            }
          }
        }
      },
      varargsInspection,
      warningLocalInspection
    };
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantUncheckedVarargs";
  }

}
