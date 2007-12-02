/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this abstract class in order to provide new inspection tool functionality. The major API limitation here is
 * subclasses should be stateless. Thus <code>check&lt;XXX&gt;</code> methods will be called in no particular order and
 * instances of this class provided by {@link InspectionToolProvider#getInspectionClasses()} will be created on demand.
 * The other important thing is problem anchors (PsiElements) reported by <code>check&lt;XXX&gt;</code> methods should
 * lie under corresponding first parameter of one method.
 *
 * @see GlobalInspectionTool
 */
public abstract class BaseJavaLocalInspectionTool extends LocalInspectionTool {
  /**
   * Override this to report problems at method level.
   *
   * @param method     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at method level.
   */
  @Nullable
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at class level.
   *
   * @param aClass     to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at class level.
   */
  @Nullable
  public ProblemDescriptor[] checkClass(@NotNull PsiClass aClass, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override this to report problems at field level.
   *
   * @param field      to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at field level.
   */
  @Nullable
  public ProblemDescriptor[] checkField(@NotNull PsiField field, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
     * Override this to report problems at file level.
     *
     * @param file       to check.
     * @param manager    InspectionManager to ask for ProblemDescriptor's from.
     * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
     * @return <code>null</code> if no problems found or not applicable at field level.
     */
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
      }

      public void visitMethod(PsiMethod method) {
        addDescriptors(checkMethod(method, holder.getManager(), isOnTheFly));
      }

      public void visitClass(PsiClass aClass) {
        addDescriptors(checkClass(aClass, holder.getManager(), isOnTheFly));
      }

      public void visitField(PsiField field) {
        addDescriptors(checkField(field, holder.getManager(), isOnTheFly));
      }

      public void visitFile(PsiFile file) {
        addDescriptors(checkFile(file, holder.getManager(), isOnTheFly));
      }
      private void addDescriptors(final ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            holder.registerProblem(descriptor);
          }
        }
      }
    };
  }


}
